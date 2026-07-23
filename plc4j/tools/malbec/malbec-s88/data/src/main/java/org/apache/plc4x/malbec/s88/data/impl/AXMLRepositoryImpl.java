package org.apache.plc4x.malbec.s88.data.impl;


import org.apache.plc4x.malbec.s88.api.*;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;

import java.util.regex.Pattern;
import rockwell.areaModel.*;

/**
 * @author Daniel
 * Implementation of S88Repository for .axml format.
 * 1. Exports the Malbec model to a .axml file so Rockwell
 * ft batch equipment editor can import it.
 *
 * 2. Imports a .axml file from Rockwell ft
 * batch equipment editor to malbec model.
 */

public class AXMLRepositoryImpl implements S88Repository {
    private final S88Storage storage;

    public AXMLRepositoryImpl(S88Storage storage) {
        this.storage = storage;
    }

    @Override
    public S88PlantModel loadPlant() {
        try (InputStream input = storage.openInput()) {
            AreaModelDocument doc = AreaModelDocument.Factory.parse(input);
            AreaModelDocument.AreaModel areaModel = doc.getAreaModel();
            S88Element root = mapToApi(areaModel);
            S88PlantModel model = new S88PlantModel(root);

            for (S88ElementClass ec : root.getElementClasses()){
                model.registerClass(ec);
            }

            return model;
        } catch (IOException | XmlException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void savePlant(S88PlantModel model) {
        try (OutputStream output = storage.openOutput()) {


            InputStream template = getClass()
                    .getResourceAsStream("/org/apache/plc4x/malbec/s88/data/template.axml");
            if (template == null) return;


            AreaModelDocument doc = AreaModelDocument.Factory.parse(template);
            AreaModelDocument.AreaModel areaModel = doc.getAreaModel();

            S88Element root = model.getRoot();
            if (root == null) return;


            areaModel.getArea().setUniqueName(cleanText(root.getId()));

            int nextId = 1;


            for (S88ElementClass ec : model.getClasses().values()) {
                if (ec.getTargetLevel() == S88Level.PROCESSCELL) {
                    ProcessCellClass pcc = areaModel.addNewProcessCellClass();
                    pcc.setUniqueName(cleanText(ec.getName()));
                    pcc.setIconFilename("");
                } else if (ec.getTargetLevel() == S88Level.UNIT) {
                    UnitClass uc = areaModel.addNewUnitClass();
                    uc.setUniqueName(cleanText(ec.getName()));
                    uc.setIconFilename("");
                }
            }


            for (S88Element cellElement : root.getChildren()) {
                if (cellElement.getLevel() != S88Level.PROCESSCELL) continue;

                rockwell.areaModel.ProcessCell pc = areaModel.addNewProcessCell();
                pc.setXPos(parseIntOrDefault(cellElement.getProperty("xPos"), 100));
                pc.setYPos(parseIntOrDefault(cellElement.getProperty("yPos"), 100));
                pc.setUniqueName(cleanText(cellElement.getId()));
                pc.setClass1(cleanText(cellElement.getElementClass().getName()));
                pc.setUniqueID(nextId++);
                pc.setLogix5000UID(0);
                pc.setMaxOwners(1);

                for (int i = 0; i < 5; i++) pc.addNewCrossInvocationString();
                for (int i = 0; i < 5; i++) pc.addNewHyperlinkString();
                pc.setERPAlias("");

                for (S88Element unitElement : cellElement.getChildren()) {
                    if (unitElement.getLevel() != S88Level.UNIT) continue;

                    rockwell.areaModel.Unit u = areaModel.addNewUnit();
                    u.setXPos(parseIntOrDefault(unitElement.getProperty("xPos"), 150));
                    u.setYPos(parseIntOrDefault(unitElement.getProperty("yPos"), 150));
                    u.setUniqueName(cleanText(unitElement.getId()));
                    u.setClass1(cleanText(unitElement.getElementClass().getName()));
                    u.setUniqueID(nextId++);
                    u.setLogix5000UID(0);
                    u.setServer("");
                    u.setMaxOwners(1);
                    for (int i = 0; i < 5; i++) u.addNewCrossInvocationString();
                    for (int i = 0; i < 5; i++) u.addNewHyperlinkString();
                    u.setERPAlias("");

                    pc.addConfiguredUnitName(cleanText(unitElement.getId()));
                }
            }


            XmlOptions options = new XmlOptions();
            options.setSavePrettyPrint();
            options.setSaveAggressiveNamespaces();
            doc.save(output, options);

        } catch (IOException | XmlException e) {
            throw new RuntimeException(e);
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String cleanText(String original) {
        if (original == null) {
            return "";
        }
        String text = original.toUpperCase().trim();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern accent = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String accentLess = accent.matcher(normalized).replaceAll("");
        accentLess = accentLess.replace("ñ", "n").replace("Ñ", "N");
        String result = accentLess.replaceAll("\\s+", "_");

        result = result.replaceAll("[^A-Za-z0-9_]", "");

        return result;
    }


    /**
     * Turns areaModel from Rockwell into
     * malbec model by mapping a Rockwell element
     * to a S88Element.
     *
     * @param areaModel model from Rockwell (set of elements)
     * @return S88Element
     */
    private S88Element mapToApi(AreaModelDocument.AreaModel areaModel) {
        S88Element root = new S88Element();
        if (areaModel == null) return root;

        root.setId(areaModel.getArea().getUniqueName());
        root.setLevel(S88Level.AREA);


        if (areaModel.getProcessCellClassArray() != null) {
            for (ProcessCellClass pcc : areaModel.getProcessCellClassArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(pcc.getUniqueName());
                ec.setTargetLevel(S88Level.PROCESSCELL);
                root.addElementClass(ec);
            }
        }
        if (areaModel.getUnitClassArray() != null) {
            for (UnitClass uc : areaModel.getUnitClassArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(uc.getUniqueName());
                ec.setTargetLevel(S88Level.UNIT);
                root.addElementClass(ec);
            }
        }

        if(areaModel.getRecipePhaseArray() != null){
            for (AreaModelDocument.AreaModel.RecipePhase rp : areaModel.getRecipePhaseArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(rp.getUniqueName());
                ec.setTargetLevel(S88Level.EQUIPMENTMODULE);
                root.addElementClass(ec);
            }
        }

        if (areaModel.getProcessCellArray() != null) {
            for (rockwell.areaModel.ProcessCell pc : areaModel.getProcessCellArray()) {
                S88Element pcElement = new S88Element();
                pcElement.setId(pc.getUniqueName());
                pcElement.setLevel(S88Level.PROCESSCELL);
                pcElement.setProperty("xPos", String.valueOf(pc.getXPos()));
                pcElement.setProperty("yPos", String.valueOf(pc.getYPos()));


                String className = pc.getClass1();
                if (className != null && !className.isEmpty()) {
                    root.getElementClasses().stream()
                            .filter(c -> c.getName().equals(className))
                            .findFirst().ifPresent(pcElement::setClass);
                }


                if (pc.sizeOfConfiguredUnitNameArray() > 0 && areaModel.getUnitArray() != null) {
                    for (String unitName : pc.getConfiguredUnitNameArray()) {
                        for (rockwell.areaModel.Unit u : areaModel.getUnitArray()) {
                            if (u.getUniqueName().equals(unitName)) {
                                S88Element unitElement = new S88Element();
                                unitElement.setId(u.getUniqueName());
                                unitElement.setLevel(S88Level.UNIT);
                                unitElement.setProperty("xPos", String.valueOf(u.getXPos()));
                                unitElement.setProperty("yPos", String.valueOf(u.getYPos()));
                                pcElement.addChild(unitElement);

                                String unitClassName = u.getClass1();
                                if (unitClassName != null && !unitClassName.isEmpty()) {
                                    root.getElementClasses().stream()
                                            .filter(c -> c.getName().equals(unitClassName))
                                            .findFirst().ifPresent(unitElement::setClass);
                                }

                                if(u.sizeOfConfiguredEquipmentModuleNameArray() > 0 && areaModel.getEquipmentModuleArray() != null) {
                                    for (String eqmName : u.getConfiguredEquipmentModuleNameArray()) {
                                        for (rockwell.areaModel.EquipmentModule eqm : areaModel.getEquipmentModuleArray()) {
                                            if (eqm.getUniqueName().equals(eqmName)) {
                                                S88Element eqmElement = new S88Element();
                                                eqmElement.setId(eqm.getUniqueName());
                                                eqmElement.setLevel(S88Level.EQUIPMENTMODULE);
                                                eqmElement.setProperty("xPos", String.valueOf(eqm.getXPos()));
                                                eqmElement.setProperty("yPos", String.valueOf(eqm.getYPos()));
                                                unitElement.addChild(eqmElement);

                                                String eqmClassName = eqm.getRecipePhase();
                                                if (eqmClassName != null && !eqmClassName.isEmpty()) {
                                                    root.getElementClasses().stream()
                                                            .filter(c -> c.getName().equals(eqmClassName))
                                                            .findFirst().ifPresent(eqmElement::setClass);
                                                }

                                                break;
                                            }
                                        }
                                    }
                                }


                                break;
                            }
                        }
                    }
                }

                root.addChild(pcElement);
            }
        }

        return root;
    }

}

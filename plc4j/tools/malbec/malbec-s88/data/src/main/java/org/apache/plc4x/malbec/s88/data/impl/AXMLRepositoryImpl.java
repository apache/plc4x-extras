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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import rockwell.areaModel.*;
import rockwell.areaModel.impl.TagClassImpl;

/**
 * @author Daniel
 * <br>
 * Implementation of S88Repository for .axml format.
 * <br>
 * 1. Exports the Malbec model to a .axml file so Rockwell
 * ft batch equipment editor can import it.
 * <br>
 * 2. Imports an .axml file from Rockwell ft
 * batch equipment editor to malbec model.
 */

public class AXMLRepositoryImpl implements S88Repository {
    private final S88Storage storage;

    public AXMLRepositoryImpl(S88Storage storage) {
        this.storage = storage;
    }

    /**
     * Turns areaModel from Rockwell .axml format into
     * malbec model by mapping a Rockwell element
     * to a S88Element.
     *
     * @return S88Element
     */
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

            if (areaModel.getEnumerationSetArray() != null){
                for(EnumerationSet enumerationSet : areaModel.getEnumerationSetArray()){
                    S88Enumeration e = new S88Enumeration(enumerationSet.getUniqueName());
                    for(EnumerationSet.Member member : enumerationSet.getMemberArray()){
                        e.setValue(member.getName(), member.getOrdinal().intValue());
                    }
                    model.registerEnumeration(e);
                }
            }

            return model;
        } catch (IOException | XmlException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Turns physical model from Malbec into
     * Rockwell areaModel .axml format.
     *
     * @param model malbec physical model.
     */
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
                pc.setXPos(parseIntOrDefault(String.valueOf(cellElement.getProperty("xPos")), 100));
                pc.setYPos(parseIntOrDefault(String.valueOf(cellElement.getProperty("yPos")), 100));
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
                    u.setXPos(parseIntOrDefault(String.valueOf(unitElement.getProperty("xPos")), 150));
                    u.setYPos(parseIntOrDefault(String.valueOf(unitElement.getProperty("yPos")), 150));
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

    private static String unitsOrEnum(String engineeringUnits, String enumerationSetName) {
        String value = (engineeringUnits != null && !engineeringUnits.trim().isEmpty())
                ? engineeringUnits : enumerationSetName;
        return value != null ? value : "";
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

                for (String utc : uc.getConfiguredUnitTagClassNameArray()){
                    Arrays.stream(areaModel.getTagClassArray())
                            .filter(tagClass -> tagClass != null && tagClass.getUniqueName().equals(utc))
                            .findFirst().
                            ifPresent(tagClass -> {
                                Map<String, Object> property =  new LinkedHashMap<>();
                                property.put("Type", DataType.fromString(tagClass.getType().toString()));
                                property.put("Eng_Units/Enum", unitsOrEnum(tagClass.getEngineeringUnits(), tagClass.getEnumerationSetName()));
                                ec.setProperty(tagClass.getUniqueName(), property);
                            });
                }

                root.addElementClass(ec);
            }
        }

        if(areaModel.getRecipePhaseArray() != null){
            for (AreaModelDocument.AreaModel.RecipePhase rp : areaModel.getRecipePhaseArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(rp.getUniqueName());
                ec.setTargetLevel(S88Level.EQUIPMENTMODULE);
                root.addElementClass(ec);

                Map<String, Object> paramsMap =  new LinkedHashMap<>();

                for (RecipePhaseParameter rpp : rp.getRecipeParameterArray()){
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("Type", DataType.fromString(rpp.getType().toString()));
                    params.put("Eng_Units/Enum", unitsOrEnum(rpp.getEngineeringUnits(), rpp.getEnumerationSetName()));
                    String def;
                    if (rpp.isSetIntegerDefault())      def = String.valueOf(rpp.getIntegerDefault());
                    else if (rpp.isSetRealDefault())    def = rpp.getRealDefault();
                    else if (rpp.isSetStringDefault())  def = rpp.getStringDefault();
                    else if (rpp.isSetEnumerationDefault()) def = rpp.getEnumerationDefault();
                    else def = null;
                    if (def != null) params.put("Default", def);

                    if (rpp.isSetIntegerMin()) params.put("Min", String.valueOf(rpp.getIntegerMin()));
                    else if (rpp.isSetRealMin()) params.put("Min", rpp.getRealMin());

                    if (rpp.isSetIntegerMax()) params.put("Max", String.valueOf(rpp.getIntegerMax()));
                    else if (rpp.isSetRealMax()) params.put("Max", rpp.getRealMax());

                    paramsMap.put(rpp.getName(), params);
                }

                ec.setProperty("Parameters", paramsMap);

                Map<String, Object> reportsMap =  new LinkedHashMap<>();
                for (RecipePhaseReport rpr : rp.getReportParameterArray()){
                    Map<String, Object> reports =  new LinkedHashMap<>();
                    reports.put("Type", DataType.fromString(rpr.getType().toString()));
                    reports.put("Eng_Units/Enum", unitsOrEnum(rpr.getEngineeringUnits(), rpr.getEnumerationSetName()));
                    reportsMap.put(rpr.getName(), reports);
                }

                ec.setProperty("Reports", reportsMap);
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

                                if(u.getTagArray() != null) {
                                    for(UnitTag tag : u.getTagArray()) {
                                        Map<String, Object> structProp = new LinkedHashMap<>();
                                        structProp.put("Type", DataType.fromString(Objects.toString(tag.getDataType(), "")));
                                        structProp.put("Eng_Units/Enum", unitsOrEnum(tag.getEngineeringUnits(), tag.getEnumerationSetName()));
                                        structProp.put("ItemName", Objects.toString(tag.getReadItemName(), ""));

                                        unitElement.setProperty(tag.getUniqueName(), structProp);
                                    }
                                }

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

                                                for (var entry : eqmElement.getElementClass().getProperties().entrySet()) {
                                                    eqmElement.setProperty(entry.getKey(), entry.getValue());
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

package org.apache.plc4x.malbec.s88.data.impl;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.plc4x.malbec.s88.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class AXMLRepositoryImpl implements S88Repository {
    private final S88Storage storage;
    private final XmlMapper mapper;

    public AXMLRepositoryImpl(S88Storage storage) {
        this.storage = storage;
        this.mapper = new XmlMapper();
    }

    @Override
    public S88PlantModel loadPlant() {
        try(InputStream input = storage.openInput()){
            RAreaModel rArea = mapper.readValue(input, RAreaModel.class);
            S88Element root = mapToApi(rArea);
            return new S88PlantModel(root);
        } catch (IOException ignored){

        }
        return null;
    }

    @Override
    public void savePlant(S88PlantModel model) {
        try (OutputStream output = storage.openOutput()) {
            // 1. Cargaríamos la plantilla base de Rockwell (con los nodos obligatorios vacíos)
            // RockwellAreaModel template = loadTemplate();

            InputStream template = getClass().getClassLoader().getResourceAsStream("template.axml");

            RAreaModel exportModel;
            if (template == null) {
                exportModel = mapper.readValue(template, RAreaModel.class);
            } else {
                exportModel = new RAreaModel();
            }


            exportModel.processCells = new ArrayList<>();
            exportModel.units = new ArrayList<>();


            mapToXml(model.getRoot(), exportModel);


            mapper.writeValue(output, exportModel);

        } catch (IOException ignored) {

        }
    }

    private S88Element mapToApi(RAreaModel rAreaModel) {
        S88Element root = new S88Element();

        if (rAreaModel != null) {
            root.setId(rAreaModel.area.uniqueName);
            root.setLevel(S88Level.AREA);
        }

        assert rAreaModel != null;
        if (rAreaModel.processCells != null) {
            for (ProcessCell pc : rAreaModel.processCells) {
                S88Element pcElement = new S88Element();
                pcElement.setId(pc.uniqueName);
                pcElement.setLevel(S88Level.PROCESSCELL);

                if (pc.xPos != null) pcElement.setProperty("xPos", pc.xPos);
                if (pc.yPos != null) pcElement.setProperty("yPos", pc.yPos);

                for (Unit unit : rAreaModel.units) {
                    S88Element unitElement = new S88Element();
                    unitElement.setId(unit.uniqueName);
                    unitElement.setLevel(S88Level.UNIT);

                    if (unit.xPos != null) unitElement.setProperty("xPos", unit.xPos);
                    if (unit.yPos != null) unitElement.setProperty("yPos", unit.yPos);

                    pcElement.addChild(unitElement);
                }

                root.addChild(pcElement);
            }
        }


        return root;
    }


    public void mapToXml(S88Element root, RAreaModel model) {
        model.area = new Area();
        model.area.uniqueName = root.getId();
        for (S88Element cellElement : root.getChildren()) {
            if (cellElement.getLevel() == S88Level.PROCESSCELL) {
                ProcessCell pCell = new ProcessCell();
                pCell.uniqueName = cellElement.getId();
                pCell.xPos = cellElement.getProperty("xPos") != null ? cellElement.getProperty("xPos") : "100";
                pCell.yPos = cellElement.getProperty("yPos") != null ? cellElement.getProperty("yPos") : "100";

                model.processCells.add(pCell);


                for (S88Element unitElement : cellElement.getChildren()) {
                    if (unitElement.getLevel() == S88Level.UNIT) {
                        Unit unit = new Unit();
                        unit.uniqueName = unitElement.getId();
                        unit.xPos = unitElement.getProperty("xPos") != null ? unitElement.getProperty("xPos") : "150";
                        unit.yPos = unitElement.getProperty("yPos") != null ? unitElement.getProperty("yPos") : "150";

                        model.units.add(unit);
                        pCell.configuredUnitName = unit.uniqueName;
                    }
                }
            }
        }

    }
}

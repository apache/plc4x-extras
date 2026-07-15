package org.apache.plc4x.malbec.s88.data.impl;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.plc4x.malbec.s88.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Daniel
 * Implementation of S88Repository for .axml format.
 * Exports the physical model to .axml so ft batch equipment editor can import it.
 */

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


            InputStream template = getClass()
                    .getResourceAsStream("/org/apache/plc4x/malbec/s88/data/template.axml");

            if(template == null) return;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(template);

            S88Element root = model.getRoot();
            if(root == null) return;
            Element docRoot = doc.getDocumentElement();
            Element area = (Element) doc.getElementsByTagName("Area").item(0);
            if(area != null){
                NodeList nameList = area.getElementsByTagName("UniqueName");
                if(nameList.getLength() > 0){
                    nameList.item(0).setTextContent(cleanText(root.getId()));
                }
            }
            int nextId = 1;
            List<Element> processCellElements = new ArrayList<>();
            List<Element> unitlElements = new ArrayList<>();


            for(S88Element cellElement : root.getChildren()) {
                if (cellElement.getLevel() != S88Level.PROCESSCELL) continue;

                Element pc = doc.createElement("ProcessCell");

                String xPos = cellElement.getProperty("xPos");
                String yPos = cellElement.getProperty("yPos");
                pc.setAttribute("XPos", xPos.isEmpty() ? "100" : xPos);
                pc.setAttribute("YPos", yPos.isEmpty() ? "100" : yPos);
                append(doc, pc, "UniqueName", cleanText(cellElement.getId()));
                append(doc, pc, "Class", "PCELL_CLS1");
                append(doc, pc, "UniqueID", String.valueOf(nextId++));
                append(doc, pc, "Logix5000UID", "0");
                append(doc, pc, "MaxOwners", "1");
                for (int i = 0; i < 5; i++) pc.appendChild(doc.createElement( "CrossInvocationString"));
                for (int i = 0; i < 5; i++) pc.appendChild(doc.createElement( "HyperlinkString"));
                append(doc, pc, "ERPAlias", "");



                for (S88Element unitElement : cellElement.getChildren()) {
                    if (unitElement.getLevel() != S88Level.UNIT) continue;

                    Element u = doc.createElement("Unit");

                    String uxPos = unitElement.getProperty("xPos");
                    String uyPos = unitElement.getProperty("yPos");
                    u.setAttribute("XPos", uxPos.isEmpty() ? "150" : uxPos);
                    u.setAttribute("YPos", uyPos.isEmpty() ? "150" : uyPos);


                    append(doc, u, "UniqueName", cleanText(unitElement.getId()));
                    append(doc, u, "Class", "UNIT_CLS1");
                    append(doc, u, "UniqueID", String.valueOf(nextId++));
                    append(doc, u, "Logix5000UID", "0");
                    u.appendChild(doc.createElement( "Server"));
                    append(doc, u, "MaxOwners", "1");
                    for (int i = 0; i < 5; i++) u.appendChild(doc.createElement( "CrossInvocationString"));
                    for (int i = 0; i < 5; i++) u.appendChild(doc.createElement( "HyperlinkString"));
                    append(doc, u, "ERPAlias", "");

                    unitlElements.add(u);
                    append(doc, pc, "ConfiguredUnitName", cleanText(unitElement.getId()));
                }

                processCellElements.add(pc);
            }

            for (Element pc : processCellElements) {
                docRoot.appendChild(pc);
            }

            for (Element u : unitlElements) {
                docRoot.appendChild(u);
            }





            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(new DOMSource(doc), new StreamResult(output));

        } catch (IOException ignored) {

        } catch (ParserConfigurationException | SAXException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private void append(Document doc, Element parent, String tag, String text) {
        Element e = doc.createElement(tag);
        e.setTextContent(text);
        parent.appendChild(e);
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

                if(pc.configuredUnitName != null && !pc.configuredUnitName.isEmpty() && rAreaModel.units != null) {
                    for (Unit unit : rAreaModel.units) {
                        S88Element unitElement = new S88Element();
                        unitElement.setId(unit.uniqueName);
                        unitElement.setLevel(S88Level.UNIT);

                        if (unit.xPos != null) unitElement.setProperty("xPos", unit.xPos);
                        if (unit.yPos != null) unitElement.setProperty("yPos", unit.yPos);

                        pcElement.addChild(unitElement);
                    }
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

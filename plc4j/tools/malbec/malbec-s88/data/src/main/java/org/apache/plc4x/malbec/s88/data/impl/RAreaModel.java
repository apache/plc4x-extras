package org.apache.plc4x.malbec.s88.data.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "AreaModel")
public class RAreaModel {

    @JacksonXmlProperty(localName = "Area")
    public Area area;


    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "ProcessCell")
    public List<ProcessCell> processCells;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Unit")
    public List<Unit> units;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Area {
    @JacksonXmlProperty(localName = "UniqueName")
    public String uniqueName;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ProcessCell {
    @JacksonXmlProperty(localName = "UniqueName")
    public String uniqueName;


    @JacksonXmlProperty(localName = "ConfiguredUnitName")
    public String configuredUnitName;

//    @JacksonXmlProperty(localName = "ProcessCellClass")
//    public String pCellClass = "PCELL_CLS1";


    @JacksonXmlProperty(isAttribute = true, localName = "XPos")
    public String xPos;

    @JacksonXmlProperty(isAttribute = true, localName = "YPos")
    public String yPos;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Unit {
    @JacksonXmlProperty(localName = "UniqueName")
    public String uniqueName;

    @JacksonXmlProperty(isAttribute = true, localName = "XPos")
    public String xPos;

    @JacksonXmlProperty(isAttribute = true, localName = "YPos")
    public String yPos;

//    @JacksonXmlProperty(localName = "ProcessCellClass")
//    public String unitClass = "UNIT_CLS1";
}
package com.uip.oneapp.export.model

import org.simpleframework.xml.*

@Root(name = "Inspection", strict = false)
data class XmlInspection(
    @field:Element(name = "Header")
    var header: XmlHeader = XmlHeader(),

    @field:Element(name = "Project")
    var project: XmlProject = XmlProject(),

    @field:Element(name = "Pipe")
    var pipe: XmlPipe = XmlPipe(),

    @field:ElementList(name = "Observations", inline = false)
    var observations: List<XmlObservation> = emptyList(),

    @field:ElementList(name = "Notes", inline = false, required = false)
    var notes: List<XmlNote> = emptyList()
)

@Root(name = "Header", strict = false)
data class XmlHeader(
    @field:Element(name = "Version")
    var version: String = "1.0",

    @field:Element(name = "Generator")
    var generator: String = "DrainQ ONE",

    @field:Element(name = "GeneratorVersion")
    var generatorVersion: String = "1.3.0",

    @field:Element(name = "ExportDate")
    var exportDate: String = "",

    @field:Element(name = "Standard")
    var standard: String = "DIN EN 13508-2:2011"
)

@Root(name = "Project", strict = false)
data class XmlProject(
    @field:Element(name = "ProjectNumber")
    var projectNumber: String = "",

    @field:Element(name = "Client")
    var client: String = "",

    @field:Element(name = "Location")
    var location: String = "",

    @field:Element(name = "InspectionDate")
    var inspectionDate: String = "",

    @field:Element(name = "Inspector")
    var inspector: String = "",

    @field:Element(name = "Weather", required = false)
    var weather: String = ""
)

@Root(name = "Pipe", strict = false)
data class XmlPipe(
    @field:Element(name = "Type")
    var type: String = "",

    @field:Element(name = "Material")
    var material: String = "",

    @field:Element(name = "Diameter")
    var diameter: String = "",

    @field:Element(name = "Length")
    var length: String = "",

    @field:Element(name = "StartNode")
    var startNode: String = "",

    @field:Element(name = "EndNode")
    var endNode: String = "",

    @field:Element(name = "CameraType", required = false)
    var cameraType: String = ""
)

@Root(name = "Observation", strict = false)
data class XmlObservation(
    @field:Attribute(name = "id")
    var id: Long = 0,

    @field:Element(name = "Position")
    var position: String = "",

    @field:Element(name = "Code")
    var code: String = "",

    @field:Element(name = "Description", required = false)
    var description: String = "",

    @field:Element(name = "PhotoReference", required = false)
    var photoReference: String = "",

    @field:Element(name = "Timestamp")
    var timestamp: String = ""
)

@Root(name = "Note", strict = false)
data class XmlNote(
    @field:Attribute(name = "id")
    var id: Long = 0,

    @field:Element(name = "Position")
    var position: String = "",

    @field:Element(name = "Text")
    var text: String = "",

    @field:Element(name = "HasAudio")
    var hasAudio: Boolean = false,

    @field:Element(name = "Timestamp")
    var timestamp: String = ""
)

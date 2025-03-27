package com.example.worldmapexplorer.utils

data class TemplateItem(val key: String, val label: String)
data class TemplateSection(
    val type: String,
    val level: Int? = null,
    val text: String? = null,
    val items: List<TemplateItem>? = null
)

val countryTemplate = listOf(
    TemplateSection(type = "header", level = 3, text = "Main Details"),
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("capital", "Capital"),
            TemplateItem("continent", "Continent"),
            TemplateItem(
                "coordinates",
                "Coordinates"
            ), // Assumes `roundedLat` and `roundedLon` are pre-calculated
            TemplateItem("area", "Area")
        )
    ),
    TemplateSection(type = "header", level = 3, text = "Additional Details"),
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("language", "Language(s)"),
            TemplateItem("population", "Population"),
            TemplateItem("borders", "Borders")
        )
    ),
    TemplateSection(type = "header", level = 5, text = "Bounding Coordinates"),
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("northernmostPoint", "North-most"),
            TemplateItem("southernmostPoint", "South-most"),
            TemplateItem("easternmostPoint", "East-most"),
            TemplateItem("westernmostPoint", "West-most")
        )
    )
)

val riverTemplate = listOf(
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("name", "Name"),
            TemplateItem("length", "Length"),
            TemplateItem("origin", "Origin"),
            TemplateItem("mouth", "Mouth"),
            TemplateItem("tributaries", "Tributaries")
        )
    )
)

val districtTemplate = listOf(
    TemplateSection(type = "header", level = 3, text = "Main Details"),
    TemplateSection(
        type = "paragraph",
        items = listOf(
            TemplateItem("state", "State"),
            TemplateItem(
                "coordinates",
                "Coordinates"
            ), // Assumes `roundedLat` and `roundedLon` are pre-calculated
            TemplateItem("area", "Area")
        )
    ),
    TemplateSection(type = "header", level = 3, text = "Additional Details"),
    TemplateSection(
        type = "paragraph",
        items = listOf(
            TemplateItem("borders", "Borders"),
            TemplateItem("summary", "Summary")
        )
    )
)

val stateTemplate = listOf(
    TemplateSection(type = "header", level = 3, text = "Main Details"),
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("country", "Country"),
            TemplateItem("capital", "Capital"),
            TemplateItem(
                "coordinates",
                "Coordinates"
            ), // Assumes `roundedLat` and `roundedLon` are pre-calculated
            TemplateItem("area", "Area")
        )
    ),
    TemplateSection(type = "header", level = 3, text = "Additional Details"),
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("borders", "Borders"),
            TemplateItem("summary", "Summary")
        )
    )
)

val otherAreaTemplate = listOf(
    TemplateSection(
        type = "list",
        items = listOf(
            TemplateItem("type", "Type"), // Assumes `roundedLat` and `roundedLon` are pre-calculated
            TemplateItem("area", "Area"),
            TemplateItem("address", "Address"),
        )
    )
)

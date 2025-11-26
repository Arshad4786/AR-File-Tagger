package com.example.arfiletagger

import java.io.Serializable // To pass between fragments/activities if needed

/**
 * Data class representing the tag information to be associated with an augmented image.
 *
 * @param filename The filename of the object/image.
 * @param filepath The file path or location associated with the object.
 * @param deadline A string representing a deadline or time-related information for the object.
 * @param imageId A unique identifier for the image, typically the augmented image's name from ARCore.
 */
data class ImageTag(
    val filename: String = "", // Renamed from 'name'
    val filepath: String = "", // Renamed from 'use'
    val deadline: String = "", // Renamed from 'timeSetter'
    val imageId: String = "" // This will be the unique ID from ARCore's image.name
) : Serializable // Marking as Serializable for potential Bundle passing

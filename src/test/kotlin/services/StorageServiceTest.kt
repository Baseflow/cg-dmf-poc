package com.baseflow.services

import kotlin.test.Test

class StorageServiceTest {

    @Test
    fun `Validate pdf file format detection`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/pdf_sample.pdf")) {
            "Missing test resource: /testdata/pdf_sample.pdf"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "application/pdf")
    }

    @Test
    fun `Validate ms-word document`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/word_sample.docx")) {
            "Missing test resource: /testdata/word_sample.docx"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }

    @Test
    fun `Validate png image`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/cg.png")) {
            "Missing test resource: /testdata/cg.png"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "image/png")
    }

    @Test
    fun `Validate jpg image`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/cg.jpg")) {
            "Missing test resource: /testdata/cg.jpg"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "image/jpeg")
    }

    @Test
    fun `Validate svg image`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/cg.svg")) {
            "Missing test resource: /testdata/cg.svg"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "image/svg+xml")
    }

    @Test
    fun `Validate zip file`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/zip_sample.zip")) {
            "Missing test resource: /testdata/zip_sample.zip"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "application/zip")
    }


    @Test
    fun `Validate bmp image`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/bmp_sample.bmp")) {
            "Missing test resource: /testdata/bmp_sample.bmp"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "image/bmp")
    }

    @Test
    fun `Validate gif image`() {
        val resource = requireNotNull(javaClass.getResource("/testdata/gif_sample.gif")) {
            "Missing test resource: /testdata/gif_sample.gif"
        }
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assert(result == "image/gif")
    }
}
package com.baseflow.services

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageServiceTest {

    private fun validateFileFormat(inputFileName: String, expectedFormat: String) = runBlocking {
        val resource = javaClass.getResource(inputFileName)
        assertNotNull(resource, "Missing test resource: ${inputFileName}")
        val bytes = resource.readBytes()
        val result = StorageService.detectFileFormat(bytes)
        assertEquals(expectedFormat, result, "Unexpected format for $inputFileName")
    }

    // documents
    @Test fun `Validate pdf file format detection`() = validateFileFormat("/testdata/pdf_sample.pdf", "application/pdf")
    @Test fun `Validate ms-word document`() = validateFileFormat("/testdata/word_sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Test fun `Validate powerpoint document`() = validateFileFormat("/testdata/pptx_sample.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    @Test fun `Validate excel document`() = validateFileFormat("/testdata/xlsx_sample.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Test fun `Validate legacy word document`() = validateFileFormat("/testdata/doc_sample.doc", "application/vnd.ms-office")
    @Test fun `Validate legacy powerpoint document`() = validateFileFormat("/testdata/ppt_sample.ppt", "application/vnd.ms-office")
    @Test fun `Validate legacy excel document`() = validateFileFormat("/testdata/xls_sample.xls", "application/vnd.ms-office")

    // Autocad
    @Test fun `Validate dwg file format detection`() = validateFileFormat("/testdata/dwg_sample.dwg", "application/acad")
    @Test fun `Validate dxf file format detection`() = validateFileFormat("/testdata/dxf_sample.dxf", "application/dxf")

    // images
    @Test fun `Validate png image`() = validateFileFormat("/testdata/cg.png", "image/png")
    @Test fun `Validate jpg image`() = validateFileFormat("/testdata/cg.jpg", "image/jpeg")
    @Test fun `Validate svg image`() = validateFileFormat("/testdata/cg.svg", "image/svg+xml")
    @Test fun `Validate tiff image`() = validateFileFormat("/testdata/tiff_sample.tiff", "image/tiff")
    @Test fun `Validate bmp image`() = validateFileFormat("/testdata/bmp_sample.bmp", "image/bmp")
    @Test fun `Validate gif image`() = validateFileFormat("/testdata/gif_sample.gif", "image/gif")
    @Test fun `Validate heic image`() = validateFileFormat("/testdata/heic_sample.heic", "image/heic")
    @Test fun `Validate webp image`() = validateFileFormat("/testdata/webp_sample.webp", "image/webp")

    // archives
    @Test fun `Validate zip file`() = validateFileFormat("/testdata/zip_sample.zip", "application/zip")
    @Test fun `Validate gzip file`() = validateFileFormat("/testdata/gzip_sample.gz", "application/gzip")
    @Test fun `Validate bzip2 file`() = validateFileFormat("/testdata/bzip2_sample.bz2", "application/x-bzip2")
    @Test fun `Validate7z file`() = validateFileFormat("/testdata/7z_sample.7z", "application/x-7z-compressed")
    @Test fun `Validate rar file`() = validateFileFormat("/testdata/rar_sample.rar", "application/vnd.rar")
    @Test fun `Validate tar file`() = validateFileFormat("/testdata/tar_sample.tar", "application/x-tar")

    // audio
    @Test fun `Validate ogg file`() = validateFileFormat("/testdata/ogg_sample.ogg", "application/ogg")
    @Test fun `Validate flac audio file`() = validateFileFormat("/testdata/flac_sample.flac", "audio/flac")
    @Test fun `Validate mp3 audio file`() = validateFileFormat("/testdata/mp3_sample.mp3", "audio/mpeg")
    @Test fun `Validate wav audio file`() = validateFileFormat("/testdata/wav_sample.wav", "audio/wav")

    @Test fun `Validate mkv video file`() = validateFileFormat("/testdata/mkv_sample.mkv", "video/x-matroska")
    @Test fun `Validate mp4 video file`() = validateFileFormat("/testdata/mp4_sample.mp4", "video/mp4")
    @Test fun `Validate avi video file`() = validateFileFormat("/testdata/avi_sample.avi", "video/x-msvideo")

    @Test
    fun `Validate missing zip resource fails`() = runBlocking {
        val missing = "/testdata/zip_sample.zip"
        val ex = assertFailsWith<AssertionError> {
            validateFileFormat(missing, "application/zip")
        }
        assertTrue(ex.message?.contains("Missing test resource") == true)
    }

    @Test
    fun `Intentionally failing test to verify CI 2`() {
        kotlin.test.fail("This test should fail on CI")
    }
}
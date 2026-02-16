package api.models;


internal class UploadResultaat(
    val bestandsLocatie: String,
    val bestandsFormaat: String? = null,
    val bestandsOmvang: Long? = null
)
package com.carelipik.app.data.transcription

/** Uses direct phone-to-provider access when configured, otherwise retains the proxy fallback. */
class PreferDeviceKeyTranscriptionGateway(
    private val hasDeviceKey: () -> Boolean,
    private val direct: RemoteTranscriptionGateway,
    private val fallback: RemoteTranscriptionGateway
) : RemoteTranscriptionGateway {
    override fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult =
        if (hasDeviceKey()) direct.transcribe(request) else fallback.transcribe(request)
}

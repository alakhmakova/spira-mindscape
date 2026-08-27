package com.spiramindscape.android.ui.ai

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import com.spiramindscape.android.data.ai.AiApi
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the composer holds between picking an attachment and sending it (BUG-030).
 *
 * A resource attachment is **an id and nothing else** — no bytes leave the phone, and the
 * server inlines what it already holds. That is why attaching a 40 MB PDF is instant, and why
 * this state has to be right: drop the id and the model sees nothing; put a data URL on the
 * chip instead and the app re-uploads a file the server already has.
 *
 * These assertions drive the real `AiChatViewModel` through a fake transport, rather than
 * re-implementing its rules in the test — a test that reproduces the logic it checks passes
 * happily while the app is broken.
 *
 * The picker's own behaviour is covered by `VisualCheckResourceAttachTest`, and what the model
 * finally receives by the backend's `AiChatServiceResourceAttachmentTest`. This is the part in
 * between, which had no test at all until 2026-08-23.
 */
@RunWith(RobolectricTestRunner::class)
class ComposerAttachmentsTest {

    /** A transport that answers nothing, so the view model can be exercised offline. */
    private class SilentChat : AiChat {
        var lastAttachments: List<AiApi.ChatAttachment> = emptyList()

        override fun streamChat(
            goalId: String?,
            message: String,
            history: List<AiApi.HistoryEntry>,
            provider: String,
            sessionType: String,
            attachments: List<AiApi.ChatAttachment>,
            sessionTotalMinutes: Int?,
            sessionRemainingSeconds: Int?,
        ): Flow<AiApi.ChatEvent> {
            lastAttachments = attachments
            return flowOf(AiApi.ChatEvent.Done)
        }

        override suspend fun listKeys() =
            listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "mistral-large-latest"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "MISTRAL"
        override suspend fun saveProvider(provider: String) = Unit
        override suspend fun getTranscript(goalId: String?) = AiApi.StoredTranscript("", "now")
        override suspend fun putTranscript(goalId: String?, content: String): String? = "now"
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    private fun chip(id: Long, name: String) =
        AiApi.ChatAttachment(name = name, mime = "", resourceId = id)

    /** A photo chip shaped like the one `ChatAttachments.readAttachment` produces. */
    private fun parkedPhoto(contents: String): AiApi.ChatAttachment {
        val file = File.createTempFile("chip-", ".bin").apply {
            writeBytes(contents.toByteArray())
            deleteOnExit()
        }
        return AiApi.ChatAttachment(
            name = "photo.jpg",
            mime = "image/jpeg",
            dataUrl = "data:image/jpeg;base64," +
                Base64.encodeToString(contents.toByteArray(), Base64.NO_WRAP),
            cachePath = file.absolutePath,
        )
    }

    @Test
    fun `a picked resource lands on the composer carrying its id and no bytes`() {
        val vm = AiChatViewModel(goalId = "1", api = SilentChat())

        vm.addComposerAttachments(listOf(chip(42, "CV.pdf")))

        val held = vm.composerAttachments.value
        assertEquals(1, held.size)
        assertEquals(42L, held[0].resourceId)
        assertEquals("CV.pdf", held[0].name)
        // Empty, deliberately: the file stays on the server.
        assertTrue(held[0].dataUrl.isEmpty())
    }

    @Test
    fun `attachments accumulate, and past the cap the OLDEST is dropped`() {
        val vm = AiChatViewModel(goalId = "1", api = SilentChat())

        (1..(ATTACH_MAX_COUNT + 2L)).forEach { vm.addComposerAttachments(listOf(chip(it, "n$it"))) }

        val held = vm.composerAttachments.value
        assertEquals(ATTACH_MAX_COUNT, held.size)
        // The thing just picked must survive, or a tap looks like it did nothing at all.
        assertEquals(ATTACH_MAX_COUNT + 2L, held.last().resourceId)
        assertEquals(3L, held.first().resourceId)
    }

    @Test
    fun `removing one attachment leaves the rest alone`() {
        val vm = AiChatViewModel(goalId = "1", api = SilentChat())
        vm.addComposerAttachments(listOf(chip(1, "one"), chip(2, "two"), chip(3, "three")))

        vm.removeComposerAttachment(vm.composerAttachments.value[1])

        assertEquals(listOf(1L, 3L), vm.composerAttachments.value.map { it.resourceId })
    }

    @Test
    fun `a resource chip survives the process being killed - BUG-050`() {
        // The camera routinely gets this process killed while it is in front, and the camera's
        // own destination Uri is `rememberSaveable`, so the PHOTO comes back afterwards. Until
        // the composer was saved too, everything attached BEFORE the photo did not — the exact
        // asymmetry the owner reported: "attach a resource, take a photo, the resource
        // disappears and the photo stays".
        val handle = SavedStateHandle()
        val before = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        before.addComposerAttachments(listOf(chip(42, "CV.pdf")))
        before.setComposerDraft("look at this")

        // Process death: the view model goes, the saved state comes back.
        val after = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)

        assertEquals(listOf(42L), after.composerAttachments.value.map { it.resourceId })
        assertEquals("CV.pdf", after.composerAttachments.value[0].name)
        assertEquals("look at this", after.composerDraft.value)
    }

    @Test
    fun `a photo comes back too, read from the copy parked in the cache`() {
        // The first round of this fix saved only the resource chips, on the grounds that a photo
        // "returns by its own road" — the camera result redelivered against a saved destination
        // Uri. True of the shot that caused the kill and of nothing else: a picked file, or a
        // photo taken earlier in the same message, was still lost silently (owner, 2026-08-24).
        // `readAttachment` now parks the bytes in the app's cache and the chip keeps the path.
        val handle = SavedStateHandle()
        val parked = parkedPhoto("ABC")
        val before = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        before.addComposerAttachments(listOf(chip(7, "Interview notes"), parked))

        val after = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)

        val names = after.composerAttachments.value.map { it.name }
        assertEquals(listOf("Interview notes", "photo.jpg"), names)
        val photo = after.composerAttachments.value.last()
        // Rebuilt from the file, byte for byte — "ABC" base64-encodes to "QUJD".
        assertEquals("data:image/jpeg;base64,QUJD", photo.dataUrl)
        assertEquals(parked.cachePath, photo.cachePath)
    }

    @Test
    fun `the BYTES never go into saved state - only the path does`() {
        // Saved state travels to the system in a Bundle, and a multi-megabyte data URL in one is
        // a TransactionTooLargeException waiting to happen. This is the constraint the whole
        // parked-file design exists to respect, so it is asserted on the saved rows themselves.
        val handle = SavedStateHandle()
        val vm = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        vm.addComposerAttachments(listOf(parkedPhoto("ABC")))

        val rows = handle.get<ArrayList<String>>("composer.chips").orEmpty()
        assertEquals(1, rows.size)
        assertTrue(
            "a saved row must not carry the attachment's bytes: ${rows[0]}",
            !rows[0].contains("base64") && !rows[0].contains("QUJD"),
        )
    }

    @Test
    fun `a chip whose parked file has gone is dropped, not restored broken`() {
        // The cache is the system's to clear, and the sweep collects anything older than a day.
        // A chip with no bytes behind it would render as a name that sends nothing.
        val handle = SavedStateHandle()
        val parked = parkedPhoto("ABC")
        val before = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        before.addComposerAttachments(listOf(parked))
        File(parked.cachePath!!).delete()

        val after = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)

        assertTrue(after.composerAttachments.value.isEmpty())
    }

    @Test
    fun `dropping a chip deletes the bytes it parked`() {
        val vm = AiChatViewModel(goalId = "1", api = SilentChat())
        val parked = parkedPhoto("ABC")
        vm.addComposerAttachments(listOf(parked))

        vm.removeComposerAttachment(vm.composerAttachments.value.single())

        assertTrue(
            "the cache copy must go with the chip, or it is litter nothing else collects",
            !File(parked.cachePath!!).exists(),
        )
    }

    @Test
    fun `sending clears what was saved, so a kill afterwards restores nothing`() {
        val handle = SavedStateHandle()
        val vm = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        vm.addComposerAttachments(listOf(chip(42, "CV.pdf")))

        vm.send("what does this say?", vm.composerAttachments.value)

        val after = AiChatViewModel(goalId = "1", api = SilentChat(), saved = handle)
        assertTrue(after.composerAttachments.value.isEmpty())
    }

    @Test
    fun `the attachment reaches the request, and the composer empties behind it`() {
        val api = SilentChat()
        val vm = AiChatViewModel(goalId = "1", api = api)
        vm.addComposerAttachments(listOf(chip(42, "CV.pdf")))

        vm.send("what does this say?", vm.composerAttachments.value)

        // It actually went out with the message…
        assertEquals(listOf(42L), api.lastAttachments.map { it.resourceId })
        // …and is gone from the composer, so the next message does not resend it.
        assertTrue(vm.composerAttachments.value.isEmpty())
    }
}

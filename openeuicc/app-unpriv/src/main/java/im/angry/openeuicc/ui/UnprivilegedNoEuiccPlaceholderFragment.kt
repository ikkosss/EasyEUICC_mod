package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.os.Bundle
import android.se.omapi.Reader
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import im.angry.easyeuicc.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class UnprivilegedNoEuiccPlaceholderFragment : Fragment(), UnprivilegedEuiccContextMarker {
    companion object {
        const val TAG = "NoEuiccPlaceholder"
    }

    private val diagnostics: TextView by lazy {
        requireView().requireViewById(R.id.no_euicc_diagnostics)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            R.layout.fragment_no_euicc_placeholder_unprivileged,
            container,
            false
        )

        view.findViewById<View>(R.id.compatibility_check).setOnClickListener {
            startActivity(Intent(requireContext(), QuickCompatibilityActivity::class.java))
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            diagnostics.text = withContext(Dispatchers.IO) { buildDiagnostics() }
        }
    }

    /**
     * Explain _why_ no eUICC was found, instead of only stating that none was.
     *
     * The most common reason for an unofficial build is that the card grants OMAPI access
     * by signing certificate hash (ARA-M) and does not know this build's certificate.
     * Without this report that case is indistinguishable from an empty SIM slot.
     */
    private suspend fun buildDiagnostics(): String = try {
        collectDiagnostics()
    } catch (e: Exception) {
        // Vendor OMAPI implementations are known to misbehave in creative ways; a broken
        // diagnostic report must never take the whole screen down with it
        Log.w(TAG, "Slot diagnostics failed", e)
        getString(R.string.no_euicc_diagnostics_omapi_error, e.javaClass.simpleName)
    }

    private suspend fun collectDiagnostics(): String {
        val service = connectSEService(requireContext())

        try {
            if (!service.isConnected) {
                return getString(R.string.no_euicc_diagnostics_omapi_unavailable)
            }

            val readers = service.readers.filter { it.name.startsWith("SIM") }
            if (readers.isEmpty()) {
                return getString(R.string.no_euicc_diagnostics_no_readers)
            }

            val isdrAidList =
                parseIsdrAidList(preferenceRepository.isdrAidListFlow.first())
            val formatChannelName =
                appContainer.customizableTextProvider::formatNonUsbChannelName
            var anyDenied = false

            return buildString {
                appendLine(getString(R.string.no_euicc_diagnostics_title))
                readers.forEach { reader ->
                    val slotId = (reader.name.removePrefix("SIM").toIntOrNull() ?: 1) - 1
                    val (status, denied) = probeReader(reader, isdrAidList)
                    anyDenied = anyDenied || denied
                    appendLine("${formatChannelName(slotId)}: $status")
                }
                appendLine(getString(R.string.no_euicc_diagnostics_signature, signatureSha1))
                if (anyDenied) {
                    append(getString(R.string.no_euicc_diagnostics_hint_unofficial))
                }
            }.trim()
        } finally {
            service.shutdown()
        }
    }

    /**
     * Returns the human-readable state of one SIM slot, plus whether access was denied by the card.
     */
    private fun probeReader(reader: Reader, isdrAidList: List<ByteArray>): Pair<String, Boolean> {
        val session = try {
            if (!reader.isSecureElementPresent) {
                return Pair(getString(R.string.no_euicc_diagnostics_slot_empty), false)
            }
            reader.openSession()
        } catch (e: Exception) {
            return Pair(
                getString(R.string.no_euicc_diagnostics_slot_error, e.javaClass.simpleName),
                false
            )
        }

        try {
            var denied = false
            isdrAidList.forEach { aid ->
                try {
                    val channel = session.openLogicalChannel(aid)
                    if (channel != null) {
                        channel.close()
                        return Pair(
                            getString(R.string.no_euicc_diagnostics_slot_ok, aid.encodeHex()),
                            false
                        )
                    }
                } catch (_: SecurityException) {
                    // The card does have an ISD-R, but its access rules do not list us
                    denied = true
                } catch (_: Exception) {
                    // Try the next AID
                }
            }

            return if (denied) {
                Pair(getString(R.string.no_euicc_diagnostics_slot_denied), true)
            } else {
                Pair(getString(R.string.no_euicc_diagnostics_slot_no_isdr), false)
            }
        } finally {
            session.close()
        }
    }

    private val signatureSha1: String
        get() = with(requireContext()) {
            packageManager.getPackageInfo(packageName, GET_SIGNING_CERTIFICATES)
                .signingInfo!!.apkContentsSigners.first().toByteArray()
                .let(MessageDigest.getInstance("SHA-1")::digest)
                .encodeHex()
        }
}

package me.hufman.androidautoidrive

import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.lang.Exception

class CarInformationDiscovery(securityAccess: SecurityAccess, carAppAssets: CarAppResources, val listener: CarInformationDiscoveryListener?) {

	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	var capabilities: Map<String, String?>? = null

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
				?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand
				?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge = sas_challenge)
		carConnection.sas_login(sas_login)
	}

	fun onCreate() {
		getCapabilities()

		onDestroy()
	}

	private fun getCapabilities() {
		val capabilities = carConnection.rhmi_getCapabilities("", 255)

		// report the capabilities to any debug view
		val stringCapabilities = capabilities
				.mapKeys { it.key as String }
				.mapValues { it.value?.toString() }
		this.capabilities = stringCapabilities
		try {
			listener?.onCapabilities(stringCapabilities)
		} catch (e: Exception) {
		}

		// report the capabilities to analytics
		val reportedKeys = setOf(
				"vehicle.type", "vehicle.country", "vehicle.productiondate",
				"hmi.type", "hmi.version", "hmi.display-width", "hmi.display-height", "hmi.role",
				"navi", "map", "tts", "inbox", "speech2text", "voice", "a4axl", "pia", "speedlock",
				"alignment-right", "touch_command"
		)
		val reportedCapabilities = capabilities.filterKeys { it is String && it in reportedKeys }
				.mapKeys { it.key as String }
				.mapValues { it.value?.toString() }
		Analytics.reportCarCapabilities(reportedCapabilities)
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			super.cds_onPropertyChangedEvent(handle, ident, propertyName, propertyValue)
		}
	}

	fun onDestroy() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: java.lang.Exception) {}
	}
}

interface CarInformationDiscoveryListener {
	fun onCapabilities(capabilities: Map<String, String?>)
}
package no.skatteetaten.aurora.mokey.service.openshift;

import no.skatteetaten.aurora.mokey.extensions.memoize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Loader for the Application Token that will be used when loading resources from Openshift that does not require
 * an authenticated user.
 *
 * @param tokenLocation the location on the file system for the file that contains the token
 * @param tokenOverride an optional override of the token that will be used instead of the one on the file system
 *                      - useful for development and testing.
 */
@Component
class ServiceAccountTokenProvider(
        @Value("\${mokey.openshift.tokenLocation}") val tokenLocation: String,
        @Value("\${mokey.openshift.token:}") val tokenOverride: String
) {

    private val logger: Logger = LoggerFactory.getLogger(ServiceAccountTokenProvider::class.java)

    //
    private val tokenMemoizer = { readToken()}.memoize()

    /**
     * Get the Application Token by using the specified tokenOverride if it is set, or else reads the token from the
     * specified file system path. Any value used will be cached forever, so potential changes on the file system will
     * not be picked up.
     *
     * @return
     */
    fun getToken() = tokenMemoizer()

    fun readToken(): String {
        return if (tokenOverride.isBlank()) {
            readTokenFromFile()
        } else {
            tokenOverride
        }
    }

    fun readTokenFromFile(): String {
        logger.info("Reading application token from tokenLocation={}", tokenLocation)
        try {
            val token: String = String(Files.readAllBytes(Paths.get(tokenLocation))).trimEnd()
            logger.debug("Read token with length={}, firstLetter={}, lastLetter={}", token.length,
                    token[0], token[token.length - 1])
            return token
        } catch (e: IOException) {
            throw IllegalStateException("tokenLocation=$tokenLocation could not be read", e)
        }
    }
}
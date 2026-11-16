/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extractor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object Formatting {

    private val urlRegex = Regex("https?://[^\\s<>\"]+")

    /**
     * Texte affichable d'une description : le HTML (YouTube) est converti en texte
     * avec ses sauts de ligne, et les URL deviennent des liens cliquables
     */
    fun description(description: Description?): AnnotatedString {
        if (description == null) return AnnotatedString("")
        val text = when (description.type()) {
            Description.Type.HTML -> {
                val withBreaks = description.content()
                    .replace(Regex("(?i)<br\\s*/?>"), "\n")
                    .replace(Regex("(?i)</p>"), "\n\n")
                Jsoup.parse(withBreaks).body().wholeText().trim()
            }
            else -> description.content().trim()
        }
        return linkify(text)
    }

    private fun linkify(text: String) = buildAnnotatedString {
        var last = 0
        val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
        for (match in urlRegex.findAll(text)) {
            append(text.substring(last, match.range.first))
            withLink(LinkAnnotation.Url(match.value, style)) { append(match.value) }
            last = match.range.last + 1
        }
        append(text.substring(last))
    }

    /** Date absolue dans la langue des réglages, ou date textuelle du service à défaut */
    fun date(
        date: org.schabi.newpipe.extractor.localization.DateWrapper?,
        textual: String?,
        language: String
    ): String? {
        val parsed = date?.offsetDateTime()
        if (parsed != null && date.isApproximation.not()) {
            return parsed.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(Locale.forLanguageTag(language))
            )
        }
        return textual?.takeIf { it.isNotBlank() }
    }

    /**
     * Date d'un élément de liste : le texte relatif du service (« il y a 2 semaines ») sauf
     * s'il s'agit d'une date brute ISO (PeerTube), alors formatée dans la langue des réglages
     */
    fun itemDate(item: StreamInfoItem, language: String): String? {
        val textual = item.textualUploadDate
        if (textual != null && !isoDate.matches(textual)) return textual
        val parsed = item.uploadDate?.offsetDateTime() ?: return textual
        return parsed.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.forLanguageTag(language))
        )
    }

    private val isoDate = Regex("\\d{4}-\\d{2}-\\d{2}T.*")

    fun count(value: Long, language: String): String? {
        if (value < 0) return null
        return NumberFormat.getCompactNumberInstance(
            Locale.forLanguageTag(language), NumberFormat.Style.SHORT
        ).format(value)
    }

    /**
     * Compteur que YouTube ne donne qu'en abrégé (abonnés, vues des pages chaîne). L'extracteur
     * ne sait lire ces abréviations qu'en anglais : « 138 k abonnés » devient 138. Hors anglais,
     * mieux vaut ne rien afficher qu'une valeur mille fois trop petite.
     */
    fun abbreviatedCount(value: Long, language: String, serviceId: Int): String? {
        val unreliable = serviceId == ServiceList.YouTube.serviceId &&
            Locale.forLanguageTag(language).language != "en"
        return if (unreliable) null else count(value, language)
    }

    fun duration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun millis(ms: Long): String = duration(ms / 1000).ifEmpty { "0:00" }

    /** Vignette la plus proche de 480 px de large */
    fun thumbnail(images: List<Image>): String? =
        images.minByOrNull { if (it.width > 0) kotlin.math.abs(it.width - 480) else 10_000 }?.url
}

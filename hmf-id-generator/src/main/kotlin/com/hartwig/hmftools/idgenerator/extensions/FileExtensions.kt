package com.hartwig.hmftools.idgenerator.extensions

import com.hartwig.hmftools.idgenerator.HASH
import com.hartwig.hmftools.idgenerator.HMF_ID
import com.hartwig.hmftools.idgenerator.HmfId
import com.hartwig.hmftools.idgenerator.PATIENT_ID
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.nio.charset.Charset

fun File.readOldIds(): Set<HmfId> {
    val parser = CSVParser.parse(this, Charset.defaultCharset(), CSVFormat.DEFAULT.withHeader())
    return parser.map { HmfId(it.get(HASH), it.get(HMF_ID).toInt()) }.toSet()
}

fun File.writeHmfIds(ids: Collection<HmfId>) {
    this.bufferedWriter().use { out ->
        out.write("$HMF_ID,$HASH,$PATIENT_ID")
        out.newLine()
        ids.sortedBy { it.id }.forEach {
            out.write("${it.id},${it.hash},${it.patientId}")
            out.newLine()
        }
    }
}
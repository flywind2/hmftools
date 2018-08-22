package com.hartwig.hmftools.knowledgebaseimporter.iclusion.readers

import com.hartwig.hmftools.knowledgebaseimporter.iclusion.IclusionEvent
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.ProteinAnnotation
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.events.EventType
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.readers.SomaticEventReader
import com.hartwig.hmftools.knowledgebaseimporter.transvar.matchers.TransvarProteinMatcher

object IclusionProteinAnnotationReader : SomaticEventReader<IclusionEvent, ProteinAnnotation> {
    private fun match(event: IclusionEvent): Boolean {
        return event.types.size == 1 && event.types.contains(EventType.MUT) && TransvarProteinMatcher.matches(event.variant)
    }

    override fun read(event: IclusionEvent): List<ProteinAnnotation> {
        if (match(event)) return listOfNotNull(ProteinAnnotation(event.transcript, event.variant))
        return emptyList()
    }
}

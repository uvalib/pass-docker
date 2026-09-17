/*
 * Copyright 2025 Johns Hopkins University
 * Copyright 2026 The Rector and Visitors of the University of Virginia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.deposit.provider.dspace;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositMetadata.Article;
import org.eclipse.pass.deposit.model.DepositMetadata.Journal;
import org.eclipse.pass.deposit.model.DepositMetadata.Manuscript;
import org.eclipse.pass.deposit.model.DepositMetadata.Person;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.springframework.stereotype.Component;

/**
 * JSON Patch for LibraOpen Publication collections (entity-type Publication).
 *
 * LibraOpen DSpace sends {@code publicationStep} and {@code traditionalpagetwo}. 
 * Stock PASS sends {@code traditionalpageone} and {@code traditionalpagetwo}
 *
 * Required LibraOpen fields: dc.title, dc.date.issued, dc.type, dc.description.abstract,
 * license granted.
 */
@Component
public class DSpaceMetadataMapper {
    static final String SECTION_ONE = "publicationStep";
    static final String SECTION_TWO = "traditionalpagetwo";
    static final String PUBLICATION_TYPE = "Article";
    static final String ABSTRACT_FALLBACK = "No abstract provided.";

    public String patchWorkspaceItem(DepositSubmission submission) {
        DepositMetadata depositMd = submission.getMetadata();
        Journal journalMd = depositMd.getJournalMetadata();
        Article articleMd = depositMd.getArticleMetadata();
        Manuscript manuscriptMd = depositMd.getManuscriptMetadata();

        JSONArray metadata = new JSONArray();

        String title = manuscriptMd.getTitle();
        metadata.add(add_array(SECTION_ONE, "dc.title", title));

        if (journalMd != null && journalMd.getPublisherName() != null) {
            metadata.add(add_array(SECTION_ONE, "dc.publisher", journalMd.getPublisherName()));
        }

        if (articleMd.getDoi() != null) {
            metadata.add(add_array(SECTION_ONE, "dc.identifier.doi", articleMd.getDoi().toString()));
        }

        String msAbstract = manuscriptMd.getMsAbstract();
        if (msAbstract == null || msAbstract.isBlank()) {
            msAbstract = (title != null && !title.isBlank()) ? title : ABSTRACT_FALLBACK;
        }
        metadata.add(add_array(SECTION_TWO, "dc.description.abstract", msAbstract));

        metadata.add(add_array(SECTION_ONE, "dc.type", PUBLICATION_TYPE));

        ZonedDateTime issued = (journalMd != null) ? journalMd.getPublicationDate() : null;
        if (issued == null) {
            issued = ZonedDateTime.now();
        }
        metadata.add(add_array(SECTION_ONE, "dc.date.issued",
                issued.format(DateTimeFormatter.ISO_LOCAL_DATE)));

        String[] authors = depositMd.getPersons().stream().filter(
                p -> p.getType() != DepositMetadata.PERSON_TYPE.submitter).
                map(Person::getName).toArray(String[]::new);

        if (authors.length > 0) {
            metadata.add(add_array(SECTION_ONE, "dc.contributor.author", authors));
        }

        metadata.add(add("license", "granted", "true"));

        return metadata.toString();
    }

    private JSONObject add(String section, String key, String value) {
        JSONObject op = new JSONObject();

        op.put("op", "add");
        op.put("path", "/sections/" + section + "/" + key);
        op.put("value", value);

        return op;
    }

    private JSONObject add_array(String section, String key, String... values) {
        JSONObject op = new JSONObject();

        op.put("op", "add");
        op.put("path", "/sections/" + section + "/" + key);

        JSONArray op_value = new JSONArray();
        for (String value : values) {
            op_value.add(array_value(value));
        }

        op.put("value", op_value);

        return op;
    }

    private JSONObject array_value(String value) {
        JSONObject obj = new JSONObject();
        obj.put("value", value);
        return obj;
    }
}

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

import static org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper.PUBLICATION_TYPE;
import static org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper.SECTION_ONE;
import static org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper.SECTION_TWO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.eclipse.pass.deposit.model.DepositManifest;
import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositMetadata.Article;
import org.eclipse.pass.deposit.model.DepositMetadata.Journal;
import org.eclipse.pass.deposit.model.DepositMetadata.Manuscript;
import org.eclipse.pass.deposit.model.DepositMetadata.PERSON_TYPE;
import org.eclipse.pass.deposit.model.DepositMetadata.Person;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.junit.jupiter.api.Test;

public class DSpaceMetadataMapperTest {
    @Test
    public void testPatchWorkspaceItem() {
        DSpaceMetadataMapper mapper = new DSpaceMetadataMapper();

        DepositSubmission ds = new DepositSubmission();
        DepositManifest manifest = new DepositManifest();
        DepositMetadata md = new DepositMetadata();

        ds.setManifest(manifest);
        ds.setMetadata(md);

        Article article = md.getArticleMetadata();
        Journal journal = md.getJournalMetadata();
        Manuscript manuscript = md.getManuscriptMetadata();

        manuscript.setTitle("this is a title");
        manuscript.setMsAbstract("This is a compelling abstract.");
        journal.setJournalTitle("journal title");
        journal.setPublisherName("publisher name");
        article.setDoi(URI.create("10.1016/j.iheduc.2015.08.004"));
        article.setIssue("1");
        article.setVolume("2");

        Person author1 = new Person();
        author1.setEmail("p1@example.com");
        author1.setFirstName("P1");
        author1.setFullName("P1 Person");
        author1.setLastName("Person");
        author1.setMiddleName("One");
        author1.setType(PERSON_TYPE.author);

        Person author2 = new Person();
        author2.setEmail("p2@example.com");
        author2.setFirstName("P2");
        author2.setFullName("P2 Person");
        author2.setLastName("Person");
        author2.setMiddleName("Two");
        author2.setType(PERSON_TYPE.author);

        md.getPersons().add(author1);
        md.getPersons().add(author2);

        ZonedDateTime pubDate = ZonedDateTime.of(2024, 12, 19, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime embargoDate = ZonedDateTime.now().plusYears(1);

        journal.setPublicationDate(pubDate);
        article.setEmbargoLiftDate(embargoDate);

        ds.setSubmissionDate(pubDate);

        String json = mapper.patchWorkspaceItem(ds);

        DocumentContext jsonContext = JsonPath.parse(json);

        assertEquals("publicationStep", SECTION_ONE);
        checkValue(jsonContext, SECTION_ONE, "dc.title", manuscript.getTitle());
        checkValue(jsonContext, SECTION_ONE, "dc.identifier.doi", article.getDoi().toString());
        checkValue(jsonContext, SECTION_TWO, "dc.description.abstract", manuscript.getMsAbstract());
        checkValue(jsonContext, SECTION_ONE, "dc.publisher", journal.getPublisherName());
        checkValue(jsonContext, SECTION_ONE, "dc.type", PUBLICATION_TYPE);
        checkValue(jsonContext, SECTION_ONE, "dc.contributor.author", "P1 Person", "P2 Person");
        checkValue(jsonContext, SECTION_ONE, "dc.date.issued",
                journal.getPublicationDate().format(DateTimeFormatter.ISO_LOCAL_DATE));

        assertTrue(jsonContext.read("$[?(@.path == '/sections/license/granted')]", List.class).size() > 0);
        assertEquals(0, ((List<?>) jsonContext.read("$[?(@.path == '/sections/traditionalpageone/dc.title')]")).size());
        assertEquals(0, ((List<?>) jsonContext.read(
                "$[?(@.path == '/sections/publicationStep/dc.identifier.citation')]")).size());
        assertEquals(0, ((List<?>) jsonContext.read(
                "$[?(@.path == '/sections/itemAccessConditions/accessConditions')]")).size());
    }

    private void checkValue(DocumentContext context, String section, String key, String... expected) {
        String path = "$[?(@.path == '/sections/" + section + "/" + key + "')].value[*].value";

        List<String> values = context.read(path);

        assertEquals(Arrays.asList(expected), values);
    }

    @Test
    public void testPatchWorkspaceItemMinimalMetadata() {
        DSpaceMetadataMapper mapper = new DSpaceMetadataMapper();

        DepositSubmission ds = new DepositSubmission();
        DepositManifest manifest = new DepositManifest();
        DepositMetadata md = new DepositMetadata();
        Journal journal = md.getJournalMetadata();

        ds.setManifest(manifest);
        ds.setMetadata(md);

        Manuscript manuscript = md.getManuscriptMetadata();
        ZonedDateTime pubDate = ZonedDateTime.now();

        manuscript.setTitle("this is a title");
        journal.setPublicationDate(pubDate);

        String json = mapper.patchWorkspaceItem(ds);

        DocumentContext jsonContext = JsonPath.parse(json);

        checkValue(jsonContext, SECTION_ONE, "dc.title", manuscript.getTitle());
        checkValue(jsonContext, SECTION_ONE, "dc.date.issued",
                journal.getPublicationDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        checkValue(jsonContext, SECTION_ONE, "dc.type", PUBLICATION_TYPE);
        checkValue(jsonContext, SECTION_TWO, "dc.description.abstract", manuscript.getTitle());
        assertEquals(0, ((List<?>) jsonContext.read(
                "$[?(@.path == '/sections/itemAccessConditions/accessConditions')]")).size());
    }
}

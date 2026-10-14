package com.iqvia.preflight.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ai.mode=assist",
        "ai.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:preflight-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PreflightControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiReturnsExpectedOutcomeForEveryDemoFile() throws Exception {
        assertScore("sample_perfect.csv", "READY");
        assertScore("sample_okayish.csv", "NEEDS_WORK");
        assertScore("sample_bad.csv", "WILL_FAIL");
    }

    @Test
    void adExportClashFileDowngradesWhenCleanColumnsMapped() throws Exception {
        String mappings = "["
                + "{\"headerName\":\"NPI\",\"mappedName\":\"NPI_NUMBER\"},"
                + "{\"headerName\":\"FIRST_NAME\",\"mappedName\":\"FIRST_NAME\"},"
                + "{\"headerName\":\"LAST_NAME\",\"mappedName\":\"LAST_NAME\"},"
                + "{\"headerName\":\"CITY\",\"mappedName\":\"EXTRA_DATA\"},"
                + "{\"headerName\":\"STATE\",\"mappedName\":\"EXTRA_DATA\"},"
                + "{\"headerName\":\"CLEAN_CITY\",\"mappedName\":\"CITY\"},"
                + "{\"headerName\":\"CLEAN_STATE\",\"mappedName\":\"STATE\"},"
                + "{\"headerName\":\"ZIP\",\"mappedName\":\"ZIP\"},"
                + "{\"headerName\":\"ADDRESS1\",\"mappedName\":\"ADDRESS_1\"}"
                + "]";
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("static/samples/ad_export_clash_example.csv")) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "ad_export_clash_example.csv", "text/csv", input);

            mockMvc.perform(multipart("/api/check")
                            .file(file)
                            .param("saveHistory", "false")
                            .param("async", "false")
                            .param("columnMappings", mappings))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.check.score").value("NEEDS_WORK"))
                    .andExpect(jsonPath("$.check.adExportClashes.length()").value(2))
                    .andExpect(jsonPath("$.check.simulatedClientListHeaders.length()").value(9));
        }
    }

    private void assertScore(String fileName, String expectedScore) throws Exception {
        String resource = "static/samples/" + fileName;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing dataset: " + resource);
            }
            MockMultipartFile file =
                    new MockMultipartFile("file", fileName, "text/csv", input);

            mockMvc.perform(multipart("/api/check")
                            .file(file)
                            .param("saveHistory", "false")
                            .param("async", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.check.score").value(expectedScore))
                    .andExpect(jsonPath("$.analysis.analysisSource").value("Java rules and built-in summary templates"))
                    .andExpect(jsonPath("$.analysis.executiveSummary").isNotEmpty())
                    .andExpect(jsonPath("$.analysis.recommendedMatchStrategy").isNotEmpty());
        }
    }
}

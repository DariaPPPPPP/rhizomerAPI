package net.rhizomik.rhizomer;

import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.rhizomik.rhizomer.model.Class;
import net.rhizomik.rhizomer.model.*;
import net.rhizomik.rhizomer.repository.ClassRepository;
import net.rhizomik.rhizomer.repository.PondRepository;
import net.rhizomik.rhizomer.repository.ServerRepository;
import net.rhizomik.rhizomer.service.SPARQLService;
import net.rhizomik.rhizomer.service.SPARQLServiceMockFactory;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationContextLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Created by http://rhizomik.net/~roberto/
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EyeApplication.class, APIStepdefs.SPARQLServiceMockConfig.class },
        loader = SpringApplicationContextLoader.class)
@WebAppConfiguration
@IntegrationTest
public class APIStepdefs {
    private static final Logger logger = LoggerFactory.getLogger(APIStepdefs.class);

    private MockMvc mockMvc;
    private ResultActions result;

    private ObjectMapper mapper = new ObjectMapper();

    @Autowired private WebApplicationContext wac;
    @Autowired private PondRepository pondRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ServerRepository serverRepository;

    @Configuration
    static class SPARQLServiceMockConfig {
        @Bean
        public SPARQLService sparqlService() {
            return SPARQLServiceMockFactory.build();
        }
    }

    @Before
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac)
                //.apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Given("^a pond with id \"([^\"]*)\"$")
    public void aPondWithId(String pondId) throws Throwable {
        pondRepository.save(new Pond(pondId));
    }

    @Given("^a class in pond \"([^\"]*)\" with URI \"([^\"]*)\", label \"([^\"]*)\" and instance count (\\d+)$")
    public void aClassInPondWithURILabelAndInstanceCount(String pondId, String classUriStr, String classLabel, int instanceCount) throws Throwable {
        Pond pond = pondRepository.findOne(pondId);
        classRepository.save(new Class(pond, new URI(classUriStr), classLabel, instanceCount));
    }

    @When("^I create a pond with id \"([^\"]*)\"$")
    public void aManagerCreatesAPondWithId(String pondId) throws Throwable {
        Pond pond = new Pond(pondId);
        String json = mapper.writeValueAsString(pond);
        this.result = mockMvc.perform(post("/ponds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .accept(MediaType.APPLICATION_JSON));
    }

    @When("^I create a class in pond \"([^\"]*)\" with URI \"([^\"]*)\", label \"([^\"]*)\" and instance count (\\d+)$")
    public void iCreateAClassWithURILabelAndInstanceCount(String pondId, String classUriStr, String classLabel, int instanceCount) throws Throwable {
        String json = MessageFormat.format("'{' " +
                "\"uri\": \"{0}\", " +
                "\"label\": \"{1}\", " +
                "\"instanceCount\": {2,number,integer} '}'", classUriStr, classLabel, instanceCount);
        this.result = mockMvc.perform(post("/ponds/{pondId}/classes", pondId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .accept(MediaType.APPLICATION_JSON));
    }

    @When("^I create facets for class \"([^\"]*)\" in pond \"([^\"]*)\"$")
    public void iCreateAFacetForClassInPondWith(String classCurie, String pondId, List<ExpectedFacet> facets) throws Throwable {
        String json = mapper.writeValueAsString(facets.get(0));

        this.result = mockMvc.perform(post("/ponds/{pondId}/classes/{classCurie}/facets", pondId, classCurie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .accept(MediaType.APPLICATION_JSON));
    }

    @Then("^the response status is (\\d+)$")
    public void theResponseStatusIs(int status) throws Throwable {
        this.result.andExpect(status().is(status));
    }

    @Then("^the response status is (\\d+) and message contains \"([^\"]*)\"$")
    public void theResponseStatusIsAndMessageContains(int status, String message) throws Throwable {
        this.result.andExpect(status().is(status))
                .andExpect(jsonPath("$.message", containsString(message)));
    }

    @And("^exists a pond with id \"([^\"]*)\"$")
    public void existsAPondWithId(String pondId) throws Throwable {
        this.result = mockMvc.perform(get("/ponds/{pondId}", pondId)
                .accept(MediaType.APPLICATION_JSON));
        this.result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(pondId)));
    }

    @And("^exists a class with id \"([^\"]*)\"$")
    public void existsAClassWithId(String classUriStr) throws Throwable {
        this.result = mockMvc.perform(get(new URI(classUriStr))
                .accept(MediaType.APPLICATION_JSON));
        this.result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(classUriStr)));
    }

    @And("^exists a facet with id \"([^\"]*)\"$")
    public void existsAFacetWithId(String facetUriStr) throws Throwable {
        this.result = mockMvc.perform(get(new URI(facetUriStr))
                .accept(MediaType.APPLICATION_JSON));
        this.result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(facetUriStr)));
    }

    @Given("^There is a pond \"([^\"]*)\" on a local server storing \"([^\"]*)\" in graph \"([^\"]*)\"$")
    public void There_is_a_pond_on_a_local_server(String pondId, String dataFile, URI graph) throws Throwable {
        Server server = new Server(new URL("http://test/sparqlmock"));
        Pond pond = new Pond(pondId);
        pond.setServer(serverRepository.save(server));
        pond.addPondGraph(graph.toString());
        pondRepository.save(pond);
        SPARQLServiceMockFactory.addData(graph.toString(), dataFile);
    }

    @And("^The query type for pond \"([^\"]*)\" is \"([^\"]*)\"$")
    public void The_query_type_for_pond_is(String pondId, String queryTypeString) throws Throwable {
        Pond pond = pondRepository.findOne(pondId);
        pond.setQueryType(Queries.QueryType.valueOf(queryTypeString));
        pondRepository.save(pond);
    }

    @And("^The inference for pond \"([^\"]*)\" is set to \"([^\"]*)\"$")
    public void theInferenceForPondIsSetTo(String pondId, boolean inference) throws Throwable {
        Pond pond = pondRepository.findOne(pondId);
        pond.setInferenceEnabled(inference);
        pondRepository.save(pond);
    }

    @And("^The following ontologies are defined for the pond \"([^\"]*)\"$")
    public void The_following_ontologies_are_defined(String pondId, List<String> ontologies) throws Throwable {
        for (String ontologyUriStr: ontologies) {
            this.result = mockMvc.perform(post("/ponds/{pondId}/ontologies", pondId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"uri\": \"" + ontologyUriStr + "\" }")
                    .accept(MediaType.APPLICATION_JSON));
            this.result.andExpect(status().isCreated());
        }
        this.result.andExpect(jsonPath("$", containsInAnyOrder(ontologies.toArray())));
    }

    @When("^I extract the classes from pond \"([^\"]*)\"$")
    public void I_extract_the_classes_from_pond(String pondId) throws Throwable {
        this.result = mockMvc.perform(get("/ponds/{pondId}/classes", pondId)
                .accept(MediaType.APPLICATION_JSON));
    }

    @When("^I extract the facets for class \"([^\"]*)\" in pond \"([^\"]*)\"$")
    public void iExtractTheFacetsForClassInPond(String classCurie, String pondId) throws Throwable {
        this.result = mockMvc.perform(get("/ponds/{pondId}/classes/{classCurie}/facets", pondId, classCurie)
                .accept(MediaType.APPLICATION_JSON));
    }

    @Then("^The retrieved classes are$")
    public void theRetrievedClassesAre(List<ExpectedClass> expectedClasses) throws Throwable {
        String json = this.result.andReturn().getResponse().getContentAsString();
        List<ExpectedClass> actualClasses = mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, ExpectedClass.class));
        assertThat(actualClasses, containsInAnyOrder(expectedClasses.toArray()));
    }

    @Then("^The retrieved class is$")
    public void theRetrievedClassIs(List<ExpectedClass> expectedClasses) throws Throwable {
        String json = this.result.andReturn().getResponse().getContentAsString();
        ExpectedClass actualClass = mapper.readValue(json, ExpectedClass.class);
        assertThat(actualClass, is(expectedClasses.get(0)));
    }

    @Then("^The retrieved facets are$")
    public void theRetrievedFacetsAre(List<ExpectedFacet> expectedFacets) throws Throwable {
        String json = this.result.andReturn().getResponse().getContentAsString();
        List<ExpectedFacet> actualFacets = mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, ExpectedFacet.class));
        assertThat(actualFacets, containsInAnyOrder(expectedFacets.toArray()));
    }

    @Then("^The retrieved facet is")
    public void theRetrievedFacetIs(List<ExpectedFacet> expectedFacets) throws Throwable {
        String json = this.result.andReturn().getResponse().getContentAsString();
        ExpectedFacet actualFacet = mapper.readValue(json, ExpectedFacet.class);
        assertThat(actualFacet, is(expectedFacets.get(0)));
    }
}

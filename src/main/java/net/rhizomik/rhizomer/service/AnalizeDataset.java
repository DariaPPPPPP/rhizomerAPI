package net.rhizomik.rhizomer.service;

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.rhizomik.rhizomer.model.*;
import net.rhizomik.rhizomer.model.Class;
import net.rhizomik.rhizomer.model.id.DatasetClassFacetId;
import net.rhizomik.rhizomer.repository.ClassRepository;
import net.rhizomik.rhizomer.repository.FacetRepository;
import net.rhizomik.rhizomer.repository.RangeRepository;
import net.rhizomik.rhizomer.repository.SPARQLEndPointRepository;
import net.rhizomik.rhizomer.service.Queries.QueryType;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

/**
 * Created by http://rhizomik.net/~roberto/
 */
@Service
public class AnalizeDataset {
    final Logger logger = LoggerFactory.getLogger(AnalizeDataset.class);

    @org.springframework.beans.factory.annotation.Value("${rhizomer.omit.properties}")
    String[] omittedProperties;
    @org.springframework.beans.factory.annotation.Value("${rhizomer.omit.classes}")
    String[] omittedClasses;

    @Autowired private PrefixCCMap prefixCCMap;
    @Autowired private SPARQLService sparqlService;
    @Autowired private OptimizedQueries optimizedQueries;
    @Autowired private DetailedQueries detailedQueries;
    @Autowired private SPARQLEndPointRepository endPointRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private FacetRepository facetRepository;
    @Autowired private RangeRepository rangeRepository;

    private Queries queries(Queries.QueryType queryType) {
        if (queryType == QueryType.DETAILED)
            return detailedQueries;
        else
            return optimizedQueries;
    }

    public void detectDatasetClasses(Dataset dataset){
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            List<String> targetGraphs = endPoint.getGraphs();
            if (dataset.isInferenceEnabled()) {
                sparqlService.inferTypes(dataset, endPoint,
                        withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
                targetGraphs.add(dataset.getDatasetInferenceGraph().toString());
            }
            ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryClasses(), targetGraphs,
                    withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            while (result.hasNext()) {
                QuerySolution soln = result.nextSolution();
                if (!soln.contains("?class")) continue;
                Resource r = soln.getResource("?class");
                if (isOmittedClass(r.getURI())) continue;
                int count = soln.getLiteral("?n").getInt();
                try {
                    Class detectedClass = new Class(dataset, new URI(r.getURI()), r.getLocalName(), count);
                    dataset.addClass(classRepository.save(detectedClass));
                    logger.info("Added detected Class {} from endpoint {}",
                            detectedClass.getId().getClassCurie(), endPoint.getQueryEndPoint());
                } catch (URISyntaxException e) {
                    logger.error("URI syntax error: {}", r.getURI());
                }
            }
        });
    }

    private boolean isOmittedClass(String uri) {
        return Arrays.stream(omittedClasses).anyMatch(uri::contains);
    }

    public void detectClassFacets(Class datasetClass) {
        endPointRepository.findByDataset(datasetClass.getDataset()).forEach(endPoint -> {
            ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                    queries(datasetClass.getDataset().getQueryType()).getQueryClassFacets(
                            datasetClass.getUri().toString(), datasetClass.getDataset().getSampleSize(),
                            datasetClass.getInstanceCount(), datasetClass.getDataset().getCoverage()),
                    endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            while (result.hasNext()) {
                QuerySolution soln = result.nextSolution();
                if (!soln.contains("?property")) continue;
                Resource property = soln.getResource("?property");
                if (isOmittedProperty(property.getURI())) continue;
                String range = XSD.xstring.toString();
                if (soln.contains("?range"))
                    range = soln.get("?range").toString();
                int uses = soln.getLiteral("?uses").getInt();
                int values = soln.getLiteral("?values").getInt();
                boolean allLiteralBoolean = false;
                if (soln.contains("?allLiteral")) {
                    Literal allLiteral = soln.getLiteral("?allLiteral");
                    if (allLiteral.getDatatype().equals(XSDDatatype.XSDboolean))
                        allLiteralBoolean = allLiteral.getBoolean();
                    else
                        allLiteralBoolean = (allLiteral.getInt() != 0);
                }
                try {
                    URI propertyUri = new URI(property.getURI());
                    DatasetClassFacetId datasetClassFacetId = new DatasetClassFacetId(datasetClass.getId(), propertyUri);
                    Facet detectedFacet = facetRepository.findById(datasetClassFacetId).orElseGet(() -> {
                        Facet newFacet = facetRepository.save(new Facet(datasetClass, propertyUri, property.getLocalName()));
                        datasetClass.addFacet(newFacet);
                        logger.info("Added detected Facet {} to Class {} in Dataset",
                                newFacet.getId().getFacetCurie(), datasetClass.getId().getClassCurie(),
                                datasetClass.getDataset().getId());
                        return newFacet;
                    });
                    URI rangeUri = new URI(range);
                    String rangeLabel = prefixCCMap.localName(range);
                    Range detectedRange = new Range(detectedFacet, rangeUri, rangeLabel, uses, values, allLiteralBoolean);
                    detectedFacet.addRange(rangeRepository.save(detectedRange));
                    facetRepository.save(detectedFacet);
                    logger.info("Added detected Range {} to Facet {} for Class {} in Dataset",
                            detectedRange.getId().getRangeCurie(), detectedFacet.getId().getFacetCurie(),
                            datasetClass.getId().getClassCurie(), datasetClass.getDataset().getId());
                } catch (URISyntaxException e) {
                    logger.error("URI syntax error: {}", property.getURI());
                }
            }
        });
    }

    private boolean isOmittedProperty(String uri) {
        return Arrays.stream(omittedProperties).anyMatch(uri::contains);
    }

    public List<Value> retrieveRangeValues(Dataset dataset, Range facetRange,
            MultiValueMap<String, String> filters, int page, int size) {
        URI classUri = facetRange.getFacet().getDomain().getUri();
        URI facetUri = facetRange.getFacet().getUri();
        List<Value> rangeValues = new ArrayList<>();
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryFacetRangeValues(classUri.toString(), facetUri.toString(),
                            facetRange.getUri().toString(), filters, facetRange.getAllLiteral(),
                            size, size * page, true),
                    endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            while (result.hasNext()) {
                QuerySolution soln = result.nextSolution();
                if (soln.contains("?value")) {
                    RDFNode value = soln.get("?value");
                    int count = soln.getLiteral("?count").getInt();
                    String label = null;
                    if (soln.contains("?label"))
                        label = soln.getLiteral("?label").getString();
                    String uri = null;
                    if (value.isResource())
                        uri = value.asResource().getURI();
                    String curie = null;
                    if (uri != null)
                        try {
                            curie = prefixCCMap.abbreviate(new URL(uri).toString());
                        } catch (Exception e) {
                        }
                    rangeValues.add(new Value(value.toString(), count, uri, curie, label));
                }
            }
        });
        return rangeValues;
    }

    public List<Value> retrieveRangeValuesContaining(Dataset dataset, Range facetRange,
           MultiValueMap<String, String> filters, String containing, int top) {
        URI classUri = facetRange.getFacet().getDomain().getUri();
        URI facetUri = facetRange.getFacet().getUri();
        List<Value> rangeValues = new ArrayList<>();
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryFacetRangeValuesContaining(classUri.toString(), facetUri.toString(),
                            facetRange.getUri().toString(), filters, facetRange.getAllLiteral(), containing, top),
                    endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            while (result.hasNext()) {
                QuerySolution soln = result.nextSolution();
                if (soln.contains("?value")) {
                    RDFNode value = soln.get("?value");
                    // int count = soln.getLiteral("?count").getInt();
                    String label = null;
                    if (soln.contains("?label"))
                        label = soln.getLiteral("?label").getString();
                    String uri = null;
                    if (value.isResource())
                        uri = value.asResource().getURI();
                    String curie = null;
                    if (uri != null)
                        try {
                            curie = prefixCCMap.abbreviate(new URL(uri).toString());
                        } catch (Exception e) {
                        }
                    rangeValues.add(new Value(value.toString(), 0, uri, curie, label));
                }
            }
        });
        return rangeValues;
    }

    public List<URI> listServerGraphs(Dataset dataset, SPARQLEndPoint endPoint) {
        ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                queries(dataset.getQueryType()).getQueryGraphs(),
                withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
        List<URI> graphs = new ArrayList<>();
        while (result.hasNext()) {
            QuerySolution soln = result.nextSolution();
            if (soln.contains("?graph")) {
                Resource graph = soln.getResource("?graph");
                graphs.add(URI.create(graph.getURI()));
            }
        }
        return graphs;
    }

    public void retrieveClassInstances(OutputStream out, Dataset dataset, Class datasetClass,
        MultiValueMap<String, String> filters, int page, int size, RDFFormat format) {
        URI classUri = datasetClass.getUri();
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            Model model = sparqlService.queryDescribe(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryClassInstances(classUri.toString(), filters, size,size * page),
                    endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            RDFDataMgr.write(out, model, format);
        });
    }

    public void getLinkedResourcesLabels(OutputStream out, Dataset dataset, Class datasetClass,
        MultiValueMap<String, String> filters, int page, int size, RDFFormat format) {
        URI classUri = datasetClass.getUri();
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            Model model = sparqlService.queryDescribe(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryClassInstancesLabels(classUri.toString(), filters, size,size * page),
                    endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            RDFDataMgr.write(out, model, format);
        });
    }

    public int retrieveClassInstancesCount(Dataset dataset, Class datasetClass, MultiValueMap<String, String> filters) {
        URI classUri = datasetClass.getUri();
        AtomicInteger count = new AtomicInteger();
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            ResultSet result = sparqlService.querySelect(endPoint.getQueryEndPoint(),
                queries(dataset.getQueryType()).getQueryClassInstancesCount(classUri.toString(), filters),
                endPoint.getGraphs(), withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            while (result.hasNext()) {
                QuerySolution soln = result.nextSolution();
                if (soln.contains("?n"))
                    count.addAndGet(soln.getLiteral("?n").getInt());
            }
        });
        return count.get();
    }

    public void describeDatasetResource(OutputStream out, Dataset dataset, URI resourceUri, RDFFormat format) {
        endPointRepository.findByDataset(dataset).forEach(endPoint -> {
            Model model = sparqlService.queryDescribe(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryDescribeResource(resourceUri), endPoint.getGraphs(),
                    withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
            model.add(sparqlService.queryDescribe(endPoint.getQueryEndPoint(),
                    queries(dataset.getQueryType()).getQueryDescribeResourceLabels(resourceUri), endPoint.getGraphs(),
                    withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword())));
            RDFDataMgr.write(out, model, format);
        });
    }

    public void browseUri(OutputStream out, URI resourceUri, RDFFormat format) {
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, resourceUri.toString());
        } catch (RiotException e) {
            logger.info("Unable to retrieve RDF from {}: {}", resourceUri, e.getMessage());
        }
        RDFDataMgr.write(out, model, format);
    }

    public long countGraphTriples(SPARQLEndPoint endPoint, String graph) {
        return sparqlService.countGraphTriples(endPoint.getQueryEndPoint(), graph,
                withCreds(endPoint.getQueryUsername(), endPoint.getQueryPassword()));
    }

    public void clearGraph(SPARQLEndPoint endPoint, String graph) {
        sparqlService.clearGraph(endPoint.getUpdateEndPoint(), graph,
                withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
    }

    public void dropGraph(SPARQLEndPoint endPoint, String graph) {
        sparqlService.dropGraph(endPoint.getUpdateEndPoint(), graph,
                withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
    }

    public void loadModel(SPARQLEndPoint endPoint, String graph, Model model) {
        sparqlService.loadModel(endPoint.getUpdateEndPoint(), endPoint.getType(), graph, model,
                withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
        endPoint.addGraph(graph);
    }

    public void clearOntologies(Dataset dataset, SPARQLEndPoint endPoint) {
        sparqlService.clearGraph(endPoint.getUpdateEndPoint(), dataset.getDatasetOntologiesGraph().toString(),
                withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
    }

    public void loadOntologies(Dataset dataset, SPARQLEndPoint endPoint, Set<String> ontologies) {
        ontologies.forEach(ontologyUriStr -> {
            sparqlService.loadURI(endPoint.getUpdateEndPoint(), endPoint.getType(),
                    dataset.getDatasetOntologiesGraph().toString(), ontologyUriStr,
                    withCreds(endPoint.getUpdateUsername(), endPoint.getUpdatePassword()));
            dataset.addDatasetOntology(ontologyUriStr);
        });
    }

    private HttpClient withCreds(String username, String password) {
        if (username == null || password == null )
            return null;
        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
        credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return HttpClients.custom().setDefaultCredentialsProvider(credsProv).build();
    }
}

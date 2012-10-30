package org.ofbiz.search.solr;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.SpellCheckResponse.Suggestion;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

public class ProductSearchServices {

	public static final String module = ProductSearchServices.class.getName();
	
	/**
     * Runs a query on the Solr Search Engine and returns the results.
     * <p>
     * This function only returns an object of type QueryResponse, so it is probably not a good idea to call it directly from within the
     * groovy files (As a decent example on how to use it, however, use keywordSearch instead).
     */
    public static Map<String, Object> runSolrQuery(DispatchContext dctx, Map<String, Object> context) {
        // get Connection
        HttpSolrServer server = null;
        Map<String, Object> result;
        try {
            server = new HttpSolrServer(SolrUtil.solrUrl);
            // create Query Object
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery((String) context.get("query"));
            // solrQuery.setQueryType("dismax");
            boolean faceted = (Boolean) context.get("facet");
            if (faceted) {
                solrQuery.setFacet(faceted);
                solrQuery.addFacetField("manu");
                solrQuery.addFacetField("cat");
                solrQuery.setFacetMinCount(1);
                solrQuery.setFacetLimit(8);

                solrQuery.addFacetQuery("listPrice:[0 TO 50]");
                solrQuery.addFacetQuery("listPrice:[50 TO 100]");
                solrQuery.addFacetQuery("listPrice:[100 TO 250]");
                solrQuery.addFacetQuery("listPrice:[250 TO 500]");
                solrQuery.addFacetQuery("listPrice:[500 TO 1000]");
                solrQuery.addFacetQuery("listPrice:[1000 TO 2500]");
                solrQuery.addFacetQuery("listPrice:[2500 TO 5000]");
                solrQuery.addFacetQuery("listPrice:[5000 TO 10000]");
                solrQuery.addFacetQuery("listPrice:[10000 TO 50000]");
                solrQuery.addFacetQuery("listPrice:[50000 TO *]");
            }
            
            boolean spellCheck = (Boolean) context.get("spellcheck");
            if(spellCheck){
            	solrQuery.setParam("spellcheck", spellCheck);
            }
            
            boolean highLight = (Boolean) context.get("highlight");
            if (highLight) {
                solrQuery.setHighlight(highLight);
                solrQuery.setHighlightSimplePre("<span class=\"highlight\">");
                solrQuery.addHighlightField("description");
                solrQuery.setHighlightSimplePost("</span>");
                solrQuery.setHighlightSnippets(2);
            }

            // Set additional Parameter
            // SolrQuery.ORDER order = SolrQuery.ORDER.desc;

            if (context.get("viewIndex") != null && (Integer) context.get("viewIndex") > 0) {
                solrQuery.setStart((Integer) context.get("viewIndex"));
            }
            if (context.get("viewSize") != null && (Integer) context.get("viewSize") > 0) {
                solrQuery.setRows((Integer) context.get("viewSize"));
            }

            // if ((List) context.get("queryFilter") != null && ((ArrayList<SolrDocument>) context.get("queryFilter")).size() > 0) {
            // List filter = (List) context.get("queryFilter");
            // String[] tn = new String[filter.size()];
            // Iterator it = filter.iterator();
            // for (int i = 0; i < filter.size(); i++) {
            // tn[i] = (String) filter.get(i);
            // }
            // solrQuery.setFilterQueries(tn);
            // }
            String queryFilter = (String) context.get("queryFilter");
            if(UtilValidate.isNotEmpty(queryFilter))
            solrQuery.setFilterQueries(queryFilter.split(" "));
            if ((String) context.get("returnFields") != null) {
                solrQuery.setFields((String) context.get("returnFields"));
            }

            // if((Boolean)context.get("sortByReverse"))order.reverse();
            if ((String) context.get("sortBy") != null && ((String) context.get("sortBy")).length() > 0) {
                SolrQuery.ORDER order;
                if (!((Boolean) context.get("sortByReverse")))
                    order = SolrQuery.ORDER.asc;
                else
                    order = SolrQuery.ORDER.desc;
                solrQuery.setSortField(((String) context.get("sortBy")).replaceFirst("-", ""), order);
            }

            if ((String) context.get("facetQuery") != null) {
                solrQuery.addFacetQuery((String) context.get("facetQuery"));
            }

            QueryResponse rsp = server.query(solrQuery);
            result = ServiceUtil.returnSuccess();
            result.put("queryResult", rsp);
        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), module);
            result = ServiceUtil.returnError(e.toString());
        }
        return result;
    }

    /**
     * Performs solr products search.
     */
    public static Map<String, Object> productsSearch(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        LocalDispatcher dispatcher = dctx.getDispatcher();

        try {
            Map<String, Object> dispatchMap = FastMap.newInstance();
            if (UtilValidate.isNotEmpty(context.get("productCategoryId"))){
            	String productCategoryId = (String) context.get("productCategoryId");
            	dispatchMap.put("query", "cat:*" + productCategoryId+"*");
            }
            else
                return ServiceUtil.returnError("Missing product category id");
            if (context.get("viewSize") != null)
                dispatchMap.put("viewSize", Integer.parseInt(((String) context.get("viewSize"))));
            if (context.get("viewIndex") != null)
                dispatchMap.put("viewIndex", Integer.parseInt((String) context.get("viewIndex")));
            if (context.get("queryFilter") != null)
                dispatchMap.put("queryFilter", context.get("queryFilter"));
            dispatchMap.put("facet", false);
            dispatchMap.put("spellcheck", true);
            dispatchMap.put("highlight", true);
            Map<String, Object> searchResult = dispatcher.runSync("runSolrQuery", dispatchMap);
            QueryResponse queryResult = (QueryResponse) searchResult.get("queryResult");
            result = ServiceUtil.returnSuccess();
            result.put("results", queryResult.getResults());
            result.put("listSize", queryResult.getResults().getNumFound());
            result.put("viewIndex", queryResult.getResults().getStart());
            result.put("viewSize", queryResult.getResults().size());
        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), module);
            result = ServiceUtil.returnError(e.toString());
        }
        return result;
    }

    /**
     * Performs keyword search.
     * <p>
     * The search form requires the result to be in a specific layout, so this will generate the proper results.
     */
    public static Map<String, Object> keywordSearch(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        LocalDispatcher dispatcher = dctx.getDispatcher();

        try {
            if (context.get("query") == null || context.get("query").equals(""))
                context.put("query", "*:*");

            Map<String, Object> dispatchMap = FastMap.newInstance();
            if (context.get("viewSize") != null)
                dispatchMap.put("viewSize", Integer.parseInt(((String) context.get("viewSize"))));
            if (context.get("viewIndex") != null)
                dispatchMap.put("viewIndex", Integer.parseInt((String) context.get("viewIndex")));
            if (context.get("query") != null)
                dispatchMap.put("query", context.get("query"));
            if (context.get("queryFilter") != null)
                dispatchMap.put("queryFilter", context.get("queryFilter"));
            dispatchMap.put("spellcheck", true);
            Map<String, Object> searchResult = dispatcher.runSync("runSolrQuery", dispatchMap);
            QueryResponse queryResult = (QueryResponse) searchResult.get("queryResult");

            List<List<String>> suggestions = FastList.newInstance();
            if (queryResult.getSpellCheckResponse() != null && queryResult.getSpellCheckResponse().getSuggestions() != null) {
                Iterator<Suggestion> iter = queryResult.getSpellCheckResponse().getSuggestions().iterator();
                while (iter.hasNext()) {
                    Suggestion resultDoc = iter.next();
                    Debug.logInfo("Suggestion " + resultDoc.getAlternatives(), module);
                    suggestions.add(resultDoc.getAlternatives());
                }
            }

            Boolean isCorrectlySpelled = true;
            if (queryResult.getSpellCheckResponse() != null) {
                isCorrectlySpelled = queryResult.getSpellCheckResponse().isCorrectlySpelled();
            }

            result = ServiceUtil.returnSuccess();
            result.put("isCorrectlySpelled", isCorrectlySpelled);

            Map<String, Integer> facetQuery = queryResult.getFacetQuery();
            Map<String, String> facetQueries = FastMap.newInstance();
            for (String fq : facetQuery.keySet()) {
                if (facetQuery.get(fq).intValue() > 0)
                    facetQueries.put(fq, fq.replaceAll("^.*\\u005B(.*)\\u005D", "$1") + " (" + facetQuery.get(fq).intValue() + ")");
            }

            Map<String, Map<String, Long>> facetFields = FastMap.newInstance();
            List<FacetField> facets = queryResult.getFacetFields();
            for (FacetField facet : facets) {
                Map<String, Long> facetEntry = FastMap.newInstance();
                List<FacetField.Count> facetEntries = facet.getValues();
                if (UtilValidate.isNotEmpty(facetEntries)) {
                    for (FacetField.Count fcount : facetEntries)
                        facetEntry.put(fcount.getName(), fcount.getCount());
                    facetFields.put(facet.getName(), facetEntry);
                }
            }

            result.put("results", queryResult.getResults());
            result.put("facetFields", facetFields);
            result.put("facetQueries", facetQueries);
            result.put("queryTime", queryResult.getElapsedTime());
            result.put("listSize", queryResult.getResults().getNumFound());
            result.put("viewIndex", queryResult.getResults().getStart());
            result.put("viewSize", queryResult.getResults().size());
            result.put("suggestions", suggestions);

        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), module);
            result = ServiceUtil.returnError(e.toString());
        }
        return result;
    }

    /**
     * Returns a map of the categories currently available under the root element.
     */
    public static Map<String, Object> getAvailableCategories(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        try {
            boolean displayProducts = false;
            if (UtilValidate.isNotEmpty(context.get("displayProducts")))
                displayProducts = (Boolean) context.get("displayProducts");

            int viewIndex = 0;
            int viewSize = 9;
            if (displayProducts) {
                viewIndex = (Integer) context.get("viewIndex");
                viewSize = (Integer) context.get("viewSize");
            }
            String catalogId = null;
            if (UtilValidate.isNotEmpty(context.get("catalogId")))
                catalogId = (String) context.get("catalogId");
            
            //String productCategoryId = (String) context.get("productCategoryId") != null ? CategoryUtil.getCategoryNameWithTrail((String) context.get("productCategoryId"), dctx): null;
            String productCategoryId = (String) context.get("productCategoryId") != null ? CategoryUtil.getCategoryNameWithTrail((String) context.get("productCategoryId"),dctx) : null;
            Debug.logInfo("productCategoryId "+productCategoryId, module);
            Map<String, Object> query = ProductSearchSolrHelper.categoriesAvailable(catalogId, productCategoryId, (String) context.get("productId"), displayProducts, viewIndex, viewSize);

            QueryResponse cat = (QueryResponse) query.get("rows");
            result = ServiceUtil.returnSuccess();
            result.put("numFound", (long) 0);
            Map<String, Object> categories = FastMap.newInstance();
            List<FacetField> catList = (List<FacetField>) cat.getFacetFields();
            for (Iterator<FacetField> catIterator = catList.iterator(); catIterator.hasNext();) {
                FacetField field = (FacetField) catIterator.next();
                List<Count> catL = (List<Count>) field.getValues();
                if (catL != null) {
                    // log.info("FacetFields = "+catL);
                    for (Iterator<Count> catIter = catL.iterator(); catIter.hasNext();) {
                        FacetField.Count f = (FacetField.Count) catIter.next();
                        if (f.getCount() > 0) {
                            categories.put(f.getName(), Long.toString(f.getCount()));
                        }
                    }
                    result.put("categories", categories);
                    result.put("numFound", cat.getResults().getNumFound());
                    // log.info("The returned map is this:"+result);
                }
            }
        } catch (Exception e) {
            result = ServiceUtil.returnError(e.toString());
            result.put("numFound", (long) 0);
        }
        return result;
    }
    
    
    /**
     * Return a map of the side deep categories.
     */
    public static Map<String, Object> getSideDeepCategories(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        try {
            String catalogId = null;
            if (UtilValidate.isNotEmpty(context.get("catalogId")))
                catalogId = (String) context.get("catalogId");
            
            String productCategoryId = (String) context.get("productCategoryId") != null ? CategoryUtil.getCategoryNameWithTrail((String) context.get("productCategoryId"),dctx) : null;
            result = ServiceUtil.returnSuccess();
            Map<String, List<Map<String, Object>>> catLevel = FastMap.newInstance();
            Debug.logInfo("productCategoryId: "+productCategoryId, module);
            
            //Add toplevel categories
            String[] trailElements = productCategoryId.split("/");
    		
            //iterate over actual results
            for(String elements : trailElements){
            	//catIds must be greater than 3 chars
            	if(elements.length()>3){
            	Debug.logInfo("elements: "+elements,module);
            	String categoryPath = CategoryUtil.getCategoryNameWithTrail(elements,dctx);
                String[] categoryPathArray = categoryPath.split("/");
            	int level = Integer.parseInt(categoryPathArray[0]);
            	String facetQuery = CategoryUtil.getFacetFilterForCategory(categoryPath, dctx);
            	//Debug.logInfo("categoryPath: "+categoryPath + " facetQuery: "+facetQuery,module);
            	Map<String, Object> query = ProductSearchSolrHelper.categoriesAvailable(catalogId, categoryPath, null, facetQuery,false, 0, 0);
                QueryResponse cat = (QueryResponse) query.get("rows");
                List<Map<String, Object>> categories = FastList.newInstance();
                
                
                List<FacetField> catList = (List<FacetField>) cat.getFacetFields();
                for (Iterator<FacetField> catIterator = catList.iterator(); catIterator.hasNext();) {
                    FacetField field = (FacetField) catIterator.next();
                    List<Count> catL = (List<Count>) field.getValues();
                    if (catL != null) {
                        for (Iterator<Count> catIter = catL.iterator(); catIter.hasNext();) {
                            FacetField.Count f = (FacetField.Count) catIter.next();
                            if (f.getCount() > 0) {
                            	Map<String, Object> catMap = FastMap.newInstance();
                            	FastList<String> iName = FastList.newInstance();
                            	iName.addAll(Arrays.asList(f.getName().split("/")));
                                //Debug.logInfo("topLevel "+topLevel,"");
                                // int l = Integer.parseInt((String) iName.getFirst());
                                catMap.put("catId",iName.getLast());
                                iName.removeFirst();
                                String path = f.getName();
                                catMap.put("path",path);
                                if(level>0){
                                	iName.removeLast();    
                                    catMap.put("parentCategory",StringUtils.join(iName, "/"));
                                }else{
                                    catMap.put("parentCategory",null);
                                }
                            	catMap.put("count", Long.toString(f.getCount()));
                            	categories.add(catMap);
                            }
                        }
                    }
                }
	    		catLevel.put("menu-"+level, categories);
            	}
            }
            result.put("categories", catLevel);
            result.put("numFound", (long) 0);
            
        } catch (Exception e) {
            result = ServiceUtil.returnError(e.toString());
            result.put("numFound", (long) 0);
        }
        return result;
    }
}
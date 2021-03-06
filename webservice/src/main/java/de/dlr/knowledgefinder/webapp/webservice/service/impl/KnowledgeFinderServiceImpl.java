/*******************************************************************************
 * Copyright 2016 DLR - German Aerospace Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package de.dlr.knowledgefinder.webapp.webservice.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dlr.knowledgefinder.webapp.webservice.exporter.BibTeXExporter;
import de.dlr.knowledgefinder.webapp.webservice.service.base.KnowledgeFinderServiceBaseImpl;
import de.dlr.knowledgefinder.webapp.webservice.solr.SimpleCount;
import de.dlr.knowledgefinder.webapp.webservice.solr.SolrConnection;
import de.dlr.knowledgefinder.webapp.webservice.solr.query.AbstractSolrQueryFactory;
import de.dlr.knowledgefinder.webapp.webservice.solr.query.SolrQueryFactoryMaker;

/**
 * The implementation of the KnowledgeFinder Webservice remote service.
 *
 * <p>
 * All custom service methods should be put in this class. Whenever methods are added, rerun ServiceBuilder to copy their definitions into the {@link de.dlr.knowledgefinder.webapp.webservice.service.KnowledgeFinderService} interface.
 *
 * <p>
 * This is a remote service. Methods of this service are expected to have security checks based on the propagated JAAS credentials because this service can be accessed remotely.
 * </p>
 *
 * 
 * @see de.dlr.knowledgefinder.webapp.webservice.service.base.KnowledgeFinderServiceBaseImpl
 * @see de.dlr.knowledgefinder.webapp.webservice.service.KnowledgeFinderServiceUtil
 */
public class KnowledgeFinderServiceImpl extends KnowledgeFinderServiceBaseImpl {
    /*
     * NOTE FOR DEVELOPERS:
     *
     * Never reference this interface directly. Always use {@link de.dlr.xps.server.knowledgefinder.webservice.service.KnowledgeFinderServiceUtil} to access the KnowledgeFinder Webservice remote service.
     */
	/** The Constant logger. */
    private final Logger logger = LoggerFactory.getLogger(KnowledgeFinderServiceImpl.class);
	private static final String ANY_VALUE = "_ANY";
	private static final String ANY_QUERY = ":[\"\" TO *]";
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, Object> getDocuments(String query, String filterQuery) throws PortalException {
		return getDocuments(query, filterQuery, null, null, null, null, null);
	}
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, Object> getDocuments(String query, String filterQuery, String fields, String start, String rows, String sort, String highlightFields) throws PortalException {
		
		User user = null;
		try {
			user = getGuestOrUser();
		} catch (SystemException e2) {}
		
		AbstractSolrQueryFactory qFactory = SolrQueryFactoryMaker.createSolrQueryFactory(user);

		Map<String, Object> resultMap = new HashMap<String, Object>();
		SolrClient client;
		String username;
		String password;
		try {
			client = SolrConnection.INSTANCE.getClient();
			username = SolrConnection.INSTANCE.getUsername();
			password = SolrConnection.INSTANCE.getPassword();
		} catch (IOException e1) {
			throw new PortalException(e1);
		}
		
		SolrQuery solrQuery = qFactory.createQuery(query, new String[]{filterQuery},
				new String[]{fields}, null, start, rows, new String[]{sort}, new String[]{highlightFields}, null);
		logger.info("getDocuments:" + solrQuery.toString());
		
		QueryResponse response;
		try {
			QueryRequest request = new QueryRequest(solrQuery);
			if(username != null && password != null)
				request.setBasicAuthCredentials(username, password);
			response = request.process(client);
			logger.debug("getDocuments - result:" + response.toString());
			Map<String, Map<String, List<String>>> hl = response.getHighlighting();
			SolrDocumentList docs = response.getResults();
			replaceHightlighting(docs, hl);
			
			resultMap.put("docs", docs);
			resultMap.put("numFound", response.getResults().getNumFound());
			resultMap.put("start", solrQuery.getStart());
			resultMap.put("rows", solrQuery.getRows());
		} catch (SolrServerException e) {
			logger.error(e.getMessage());
			throw new PortalException(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return resultMap;
	}
	
	private void replaceHightlighting(SolrDocumentList result, Map<String, Map<String, List<String>>> hl){
		if (hl != null && hl.size() > 0){
			for (SolrDocument res : result){
				Map<String, Object> fields = res.getFieldValueMap();
				String id = (String) fields.get("id");
				Map<String, List<String>> hlValueMap = hl.get(id);
				if (hlValueMap != null){
					for(String hlValueMapKey : hlValueMap.keySet()){
						List<String> hlValue = hlValueMap.get(hlValueMapKey);
						if(hlValue.size() > 0){
							res.setField(hlValueMapKey, hlValue);
						}
					}
				}
			}
		}
	}
	
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, List<SimpleCount>> getNodes(String query, String filterQuery, String groups) throws PortalException {
		return getNodes(query, filterQuery, groups, null, null);
	}
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, List<SimpleCount>> getNodes(String query, String filterQuery, String groups, String groupsAnyValue, String limit) throws PortalException {
		User user = null;
		try {
			user = getGuestOrUser();
		} catch (SystemException e2) {}
		
		AbstractSolrQueryFactory qFactory = SolrQueryFactoryMaker.createSolrQueryFactory(user);
		
		
		Map<String, List<SimpleCount>> nodesMap = new HashMap<String, List<SimpleCount>>();

		SolrClient client;
		String username;
		String password;
		try {
			client = SolrConnection.INSTANCE.getClient();
			username = SolrConnection.INSTANCE.getUsername();
			password = SolrConnection.INSTANCE.getPassword();
		} catch (IOException e1) {
			throw new PortalException(e1);
		}
		
		QueryResponse response;
		
		// normal facets
		if (groups != null && groups != ""){
			// TODO Apply Factory

			SolrQuery solrQuery = qFactory.createQuery(query, new String[]{filterQuery}, 
					null, new String[]{groups}, "0", "0", null, null, limit);
			logger.info("getNotes:" + solrQuery.toString());
			try {
				QueryRequest request = new QueryRequest(solrQuery);
				if(username != null && password != null)
					request.setBasicAuthCredentials(username, password);
				response = request.process(client);
				List<FacetField> facetFields = response.getFacetFields();
				if (facetFields != null){
					for (FacetField fField : facetFields){
						List<SimpleCount> nodes = new ArrayList<SimpleCount>();
						nodes.addAll(SimpleCount.transform(fField.getValues()));
						nodesMap.put(fField.getName(), nodes);
					}
				}
			} catch (SolrServerException e) {
				logger.error(e.getMessage());
				throw new PortalException(e);
			} catch (IOException e1) {
				logger.error(e1.getMessage());
			}
		}
		// getting ANY count:
		if (groupsAnyValue != null && groupsAnyValue != ""){
			List<String> groupsList = Arrays.asList(groupsAnyValue.split(","));
			for(String group : groupsList){
				String queryField = group.replace(" ", "\\ ") + ANY_QUERY;
				
				SolrQuery solrAnyQuery = qFactory.createQuery(query, new String[]{filterQuery, queryField},
						null, null, "0", "0", null, null, null);
				logger.info("getNotes (any):" + solrAnyQuery.toString());
				try {
					QueryRequest request = new QueryRequest(solrAnyQuery);
					if(username != null && password != null)
						request.setBasicAuthCredentials(username, password);
					response = request.process(client);
					long anyCountVal = (long) response.getResults().getNumFound();
					SimpleCount anyCountnew = new SimpleCount(ANY_VALUE, queryField, anyCountVal);
					List<SimpleCount> currentValues = nodesMap.get(group);
					if (currentValues == null){
						currentValues  = new ArrayList<SimpleCount>();
					}
					currentValues.add(anyCountnew);
					nodesMap.put(group, currentValues);
					
				} catch (SolrServerException e) {
					logger.error(e.getMessage());
					throw new PortalException(e);
				} catch (IOException e1) {
					logger.error(e1.getMessage());
				}
			}
		}
		return nodesMap;
	}
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, Object> exportDocuments(String query, String filterQuery, String exportType) throws PortalException {
		return exportDocuments(query, filterQuery, null, exportType);
	}
	
	@com.liferay.portal.security.ac.AccessControlled(guestAccessEnabled=true, hostAllowedValidationEnabled=false)
	public Map<String, Object> exportDocuments(String query, String filterQuery, String fields, String exportType) throws PortalException {
		logger.info("exportDocuments:" + query);
		
		if(!exportType.equalsIgnoreCase("bibtex")){ //bibtex is currently the only supported type
			throw new IllegalArgumentException("No valid export type was given: " + exportType);
		}
		
		User user = null;
		try {
			user = getGuestOrUser();
		} catch (SystemException e2) {}
		
		AbstractSolrQueryFactory qFactory = SolrQueryFactoryMaker.createSolrQueryFactory(user);

		Map<String, Object> resultMap = new HashMap<String, Object>();
		SolrClient client;
		String username;
		String password;
		try {
			client = SolrConnection.INSTANCE.getClient();
			username = SolrConnection.INSTANCE.getUsername();
			password = SolrConnection.INSTANCE.getPassword();
		} catch (IOException e1) {
			throw new PortalException(e1);
		}
		
		SolrQuery solrQuery = qFactory.createQuery(query, new String[]{filterQuery},
				new String[]{fields}, null, null, null, null, null, null);
		logger.info("exportDocuments:" + solrQuery.toString());
		QueryResponse response;
		try {
			QueryRequest request = new QueryRequest(solrQuery);
			if(username != null && password != null)
				request.setBasicAuthCredentials(username, password);
			response = request.process(client);
			SolrDocumentList docs = response.getResults();
			
			BibTeXExporter exporter;
			String exportString = "";
			exporter = new BibTeXExporter();
			exportString = exporter.export(docs);
			
			resultMap.put("docs", docs);
			resultMap.put("exportString", exportString);
			resultMap.put("numFound", response.getResults().getNumFound());
		} catch (SolrServerException e) {
			logger.error(e.getMessage());
			throw new PortalException(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return resultMap;
	}
}

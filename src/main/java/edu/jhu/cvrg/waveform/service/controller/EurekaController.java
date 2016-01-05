package edu.jhu.cvrg.waveform.service.controller;

import org.apache.log4j.Logger;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;

import edu.jhu.cvrg.data.dto.DocumentRecordDTO;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.factory.ConnectionFactory;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.waveform.service.model.EurekaInput;

/**
 * Controller to Eureka Import process
 * 
 * @author avilard4
 *
 */
public class EurekaController {
	
	private static EurekaController singleton;
	private Logger log = Logger.getLogger(EurekaController.class);
	
	public static EurekaController getInstance(){
		if(singleton == null){
			singleton = new EurekaController();
		}
		return singleton;
	}
	
	private EurekaController() {
	}
	
	/**
	 * Connect to the database and create the requested virtual tree structure.
	 * 
	 * @param input Requested JSON data, converted into a EurekaInput
	 * @throws SystemException
	 * @throws PortalException
	 * @throws DataStorageException
	 */
	public void eurekaImportProcess(EurekaInput input) throws SystemException, PortalException, DataStorageException {
		User user = initializeLiferayPermissionChecker(input.getUserId());
    	
		if(user != null){
			log.info("Data Received! " + "[User: " + user.getFirstName() + " " + user.getLastName() + " QueryName: " + input.getQueryName() + "]");
	    	
	    	Connection c = ConnectionFactory.createConnection();
	    	Long nodeId = c.storeVirtualNode(user.getUserId(), input.getQueryName(), null, null);
	    	
	    	if(input.getSubjects() != null){
	        	for(String subject : input.getSubjects()){
	        		long parentId = c.storeVirtualNode(user.getUserId(), subject, null, nodeId);
	        		DocumentRecordDTO doc = c.getDocumentRecordBySubjectId(subject, true);
	        		if(doc != null){
	        			c.storeVirtualDocument(user.getUserId(), doc.getDocumentRecordId(), parentId, subject);
	        		}
	        	}
	    	}
		}
	}
	
	private User initializeLiferayPermissionChecker(String screenName) throws SystemException, PortalException {
    	long companyId = 0L;
		
		PrincipalThreadLocal.setName(screenName);
		
		if(CompanyLocalServiceUtil.getCompaniesCount() > 0){
			companyId = CompanyLocalServiceUtil.getCompanies().get(0).getCompanyId();
		}
		log.info("Company ID = "+ companyId);
		
		User user = UserLocalServiceUtil.getUserByScreenName(companyId, screenName);
        PermissionChecker permissionChecker;
		try {
			permissionChecker = PermissionCheckerFactoryUtil.create(user);
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		} catch (Exception e) {
			log.error("EurekaImport - Error on initialize the permission checker.");
			user = null;
		}
        return user;
	
	}
	
}

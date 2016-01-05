package edu.jhu.cvrg.waveform.service.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EurekaInput {

	private static final int MAX_AGE = 5*60*1000;
	
	private String userId;
	private String queryName;
	private List<String> subjects;
	private Date creationDate;
	
	public EurekaInput() {
		creationDate = new Date();
	}
	
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public String getQueryName() {
		return queryName;
	}
	public void setQueryName(String queryName) {
		this.queryName = queryName;
	}
	public List<String> getSubjects() {
		return subjects;
	}
	public void setSubjects(List<String> subjects) {
		this.subjects = subjects;
	}
	public void addSubject(String subject){
		if(subjects == null){
			subjects = new ArrayList<String>();
		}
		subjects.add(subject);
	}
	
	/**
	 * Indicate when the object could be removed from temporary session
	 * 
	 */
	public boolean removeMe(){
		return (System.currentTimeMillis() - this.creationDate.getTime() >= MAX_AGE); 
	}
}


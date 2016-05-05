/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.timetable.solver;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.cpsolver.ifs.util.DataProperties;

/**
 * @author Tomas Muller
 */
public interface CommonSolverInterface {
	public Date getLoadedDate();

	public void start();
	public boolean isRunning();
	public void stopSolver();
	public void restoreBest();
	public void saveBest();
	public Map<String,String> currentSolutionInfo();
	public Map<String,String> bestSolutionInfo();
    public Map<String,String> statusSolutionInfo();
	public boolean isWorking();
	public void clear();

    public Map getProgress();
	public String getLog();
	public String getLog(int level, boolean includeDate);
	public String getLog(int level, boolean includeDate, String fromStage); 
	
    public DataProperties getProperties();
    public void setProperties(DataProperties config);
    
    public void save();
    public byte[] exportXml() throws IOException;
}

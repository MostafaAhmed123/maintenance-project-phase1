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
package org.unitime.timetable.solver.studentsct;

import java.io.File;
import java.util.Date;

import org.cpsolver.ifs.util.CSVFile;
import org.cpsolver.ifs.util.DataProperties;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.solver.CommonSolverInterface;


/**
 * @author Tomas Muller
 */
public interface StudentSolverProxy extends OnlineSectioningServer, CommonSolverInterface {

    public String getHost();
    public String getUser();
    public void dispose();
    
    public void load(DataProperties properties);
    public void reload(DataProperties properties);
    public Date getLoadedDate();
    
    public int getDebugLevel();
    public void setDebugLevel(int level);

    public boolean backup(File folder, String ownerId);
    public boolean restore(File folder, String ownerId);
    public boolean restore(File folder, String ownerId, boolean removeFiles);
    
    public long timeFromLastUsed();
    public boolean isPassivated();
    public boolean activateIfNeeded();
    public boolean passivate(File folder, String puid);
    public boolean passivateIfNeeded(File folder, String puid);
    public Date getLastUsed();
    
    public void interrupt();
    
    public CSVFile getReport(DataProperties parameters);
}

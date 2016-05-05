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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.unitime.localization.impl.Localization;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface.TimeBlock;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface.TimeBlockComparator;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.EventDateMapping;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.model.dao.MeetingDAO;
import org.unitime.timetable.solver.course.ui.ClassTimeInfo;
import org.unitime.timetable.solver.ui.AssignmentPreferenceInfo;
import org.unitime.timetable.util.DefaultRoomAvailabilityService;
import org.unitime.timetable.util.RoomAvailability;
import org.unitime.timetable.util.DefaultRoomAvailabilityService.MeetingTimeBlock;


/**
 * @author Tomas Muller
 */
public class CommitedClassAssignmentProxy implements ClassAssignmentProxy {
	private static AssignmentPreferenceInfo sCommitedAssignmentPreferenceInfo = new AssignmentPreferenceInfo();
	
	public CommitedClassAssignmentProxy() {}
	
	public Assignment getAssignment(Long classId) {
		return getAssignment((new Class_DAO()).get(classId));
	}
	
	public Assignment getAssignment(Class_ clazz) {
		return clazz.getCommittedAssignment();
	}
	
	public AssignmentPreferenceInfo getAssignmentInfo(Long classId) {
		return sCommitedAssignmentPreferenceInfo;
	}
    
    public AssignmentPreferenceInfo getAssignmentInfo(Class_ clazz) {
    	return sCommitedAssignmentPreferenceInfo;
    }	
    
	public Hashtable getAssignmentTable(Collection classesOrClassIds) {
		Hashtable assignments = new Hashtable();
		for (Iterator i=classesOrClassIds.iterator();i.hasNext();) {
			Object classOrClassId = i.next();
			if (classOrClassId instanceof Object[]) classOrClassId = ((Object[])classOrClassId)[0];
			Assignment assignment = (classOrClassId instanceof Class_ ? getAssignment((Class_)classOrClassId) : getAssignment((Long)classOrClassId));
			if (assignment!=null)
				assignments.put(classOrClassId instanceof Class_ ? ((Class_)classOrClassId).getUniqueId() : (Long)classOrClassId, assignment);
		}
		return assignments;
	}
	
	public Hashtable getAssignmentInfoTable(Collection classesOrClassIds) {
		Hashtable infos = new Hashtable();
		for (Iterator i=classesOrClassIds.iterator();i.hasNext();) {
			Object classOrClassId = i.next();
			if (classOrClassId instanceof Object[]) classOrClassId = ((Object[])classOrClassId)[0];
			AssignmentPreferenceInfo info = (classOrClassId instanceof Class_ ? getAssignmentInfo((Class_)classOrClassId) : getAssignmentInfo((Long)classOrClassId));
			if (info!=null)
				infos.put(classOrClassId instanceof Class_ ? ((Class_)classOrClassId).getUniqueId() : (Long)classOrClassId, info);
		}
		return infos;
	}
	
	@Override
	public boolean hasConflicts(Long offeringId) {
		InstructionalOffering offering = InstructionalOfferingDAO.getInstance().get(offeringId);
		if (offering == null || offering.isNotOffered()) return false;
		
		for (InstrOfferingConfig config: offering.getInstrOfferingConfigs())
			for (SchedulingSubpart subpart: config.getSchedulingSubparts())
				for (Class_ clazz: subpart.getClasses()) {
					if (clazz.isCancelled()) continue;
					Assignment assignment = getAssignment(clazz);
					if (assignment == null) continue;
					if (assignment.getRooms() != null)
						for (Location room : assignment.getRooms()) {
							if (!room.isIgnoreRoomCheck()) {
								for (Assignment a : room.getCommitedAssignments())
									if (!assignment.equals(a) && !a.getClazz().isCancelled() && assignment.overlaps(a) && !clazz.canShareRoom(a.getClazz()))
										return true;
			            	}
			            }
					
					if (clazz.getClassInstructors() != null)
						for (ClassInstructor instructor: clazz.getClassInstructors()) {
							if (!instructor.isLead()) continue;
							for (DepartmentalInstructor di: DepartmentalInstructor.getAllForInstructor(instructor.getInstructor())) {
								for (ClassInstructor ci : di.getClasses()) {
				            		if (ci.equals(instructor)) continue;
				            		Assignment a = getAssignment(ci.getClassInstructing());
				            		if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a) && !clazz.canShareInstructor(a.getClazz()))
				            			return true;
				            	}
			            	}
						}
					
			        Class_ parent = clazz.getParentClass();
			        while (parent!=null) {
			        	Assignment a = getAssignment(parent);
			        	if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a))
			        		return true;
			        	parent = parent.getParentClass();
			        }
			        
			        for (Iterator<SchedulingSubpart> i = clazz.getSchedulingSubpart().getInstrOfferingConfig().getSchedulingSubparts().iterator(); i.hasNext();) {
			        	SchedulingSubpart ss = i.next();
			        	if (ss.getClasses().size() == 1) {
			        		Class_ child = ss.getClasses().iterator().next();
			        		if (clazz.equals(child)) continue;
			        		Assignment a = getAssignment(child);
			        		if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a))
			        			return true;
			        	}
			        }
				}
		
		if (RoomAvailability.getInstance() != null && RoomAvailability.getInstance() instanceof DefaultRoomAvailabilityService) {
 			boolean changePast = ApplicationProperty.ClassAssignmentChangePastMeetings.isTrue();
 			boolean ignorePast = ApplicationProperty.ClassAssignmentIgnorePastMeetings.isTrue();
 			if (!changePast || ignorePast) {
				Calendar cal = Calendar.getInstance(Localization.getJavaLocale());
 				cal.set(Calendar.HOUR_OF_DAY, 0);
 				cal.set(Calendar.MINUTE, 0);
 				cal.set(Calendar.SECOND, 0);
 				cal.set(Calendar.MILLISECOND, 0);
 				Date today = cal.getTime();
 				return ((Number)MeetingDAO.getInstance().getSession().createQuery(
						"select count(mx) from ClassEvent e inner join e.meetings m, Meeting mx " +
						"where e.clazz.schedulingSubpart.instrOfferingConfig.instructionalOffering.uniqueId = :offeringId and mx.event.class != ClassEvent and m.approvalStatus = 1 and mx.approvalStatus = 1 and " +
						"m.locationPermanentId = mx.locationPermanentId and m.meetingDate = mx.meetingDate and " +
						"m.startPeriod < mx.stopPeriod and m.stopPeriod > mx.startPeriod and mx.meetingDate >= :today"
						).setLong("offeringId", offeringId).setDate("today", today).setCacheable(true).uniqueResult()).intValue() > 0;
			} else {
				return ((Number)MeetingDAO.getInstance().getSession().createQuery(
						"select count(mx) from ClassEvent e inner join e.meetings m, Meeting mx " +
						"where e.clazz.schedulingSubpart.instrOfferingConfig.instructionalOffering.uniqueId = :offeringId and mx.event.class != ClassEvent and m.approvalStatus = 1 and mx.approvalStatus = 1 and " +
						"m.locationPermanentId = mx.locationPermanentId and m.meetingDate = mx.meetingDate and " +
						"m.startPeriod < mx.stopPeriod and m.stopPeriod > mx.startPeriod"
						).setLong("offeringId", offeringId).setCacheable(true).uniqueResult()).intValue() > 0;
			}
		} else if (RoomAvailability.getInstance() != null) {
        	Date[] bounds = DatePattern.getBounds(offering.getSessionId());
 			boolean changePast = ApplicationProperty.ClassAssignmentChangePastMeetings.isTrue();
 			boolean ignorePast = ApplicationProperty.ClassAssignmentIgnorePastMeetings.isTrue();
 			Calendar cal = Calendar.getInstance(Locale.US);
    		cal.setTime(new Date());
    		cal.set(Calendar.HOUR_OF_DAY, 0);
    		cal.set(Calendar.MINUTE, 0);
    		cal.set(Calendar.SECOND, 0);
    		cal.set(Calendar.MILLISECOND, 0);
    		Date today = cal.getTime();
			for (InstrOfferingConfig config: offering.getInstrOfferingConfigs())
				for (SchedulingSubpart subpart: config.getSchedulingSubparts())
					for (Class_ clazz: subpart.getClasses()) {
						Assignment assignment = getAssignment(clazz);
						if (assignment != null && assignment.getRooms() != null && !assignment.getRooms().isEmpty()) {
				    		ClassTimeInfo period = new ClassTimeInfo(assignment);

				    		for (Location room : assignment.getRooms()) {
								if (!room.isIgnoreRoomCheck()) {
						    		Collection<TimeBlock> times = RoomAvailability.getInstance().getRoomAvailability(
						                    room.getUniqueId(),
						                    bounds[0], bounds[1], 
						                    RoomAvailabilityInterface.sClassType);
						    		if (times != null && !times.isEmpty()) {
						    			Collection<TimeBlock> timesToCheck = null;
						    			if (!changePast || ignorePast) {
						        			timesToCheck = new Vector();
						        			for (TimeBlock time: times) {
						        				if (!time.getEndTime().before(today))
						        					timesToCheck.add(time);
						        			}
						        		} else {
						        			timesToCheck = times;
						        		}
						        		if (period.overlaps(timesToCheck) != null) return true;
						    		}
								}
				    		}
						}
					}
		}
		
		return false;
	}

	@Override
	public Set<Assignment> getConflicts(Long classId) {
		if (classId == null) return null;
		Class_ clazz = Class_DAO.getInstance().get(classId);
		if (clazz == null || clazz.isCancelled()) return null;
		Assignment assignment = getAssignment(clazz);
		if (assignment == null) return null;
		Set<Assignment> conflicts = new HashSet<Assignment>();
		if (assignment.getRooms() != null)
			for (Location room : assignment.getRooms()) {
				if (!room.isIgnoreRoomCheck()) {
					for (Assignment a : room.getCommitedAssignments())
						if (!assignment.equals(a) && !a.getClazz().isCancelled() && assignment.overlaps(a) && !clazz.canShareRoom(a.getClazz()))
							conflicts.add(a);
            	}
            }
		
		if (clazz.getClassInstructors() != null)
			for (ClassInstructor instructor: clazz.getClassInstructors()) {
				if (!instructor.isLead()) continue;
				for (DepartmentalInstructor di: DepartmentalInstructor.getAllForInstructor(instructor.getInstructor())) {
					for (ClassInstructor ci : di.getClasses()) {
	            		if (ci.equals(instructor)) continue;
	            		Assignment a = getAssignment(ci.getClassInstructing());
	            		if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a) && !clazz.canShareInstructor(a.getClazz()))
	            			conflicts.add(a);
	            	}
            	}
			}
		
        Class_ parent = clazz.getParentClass();
        while (parent!=null) {
        	Assignment a = getAssignment(parent);
        	if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a))
    			conflicts.add(a);
        	parent = parent.getParentClass();
        }
        
        Queue<Class_> children = new LinkedList(clazz.getChildClasses());
        Class_ child = null;
        while ((child=children.poll())!=null) {
        	Assignment a = getAssignment(child);
        	if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a))
    			conflicts.add(a);
        	if (!child.getChildClasses().isEmpty())
        		children.addAll(child.getChildClasses());
        }
        
        for (Iterator<SchedulingSubpart> i = clazz.getSchedulingSubpart().getInstrOfferingConfig().getSchedulingSubparts().iterator(); i.hasNext();) {
        	SchedulingSubpart ss = i.next();
        	if (ss.getClasses().size() == 1) {
        		child = ss.getClasses().iterator().next();
        		if (clazz.equals(child)) continue;
        		Assignment a = getAssignment(child);
        		if (a != null && !a.getClazz().isCancelled() && assignment.overlaps(a))
        			conflicts.add(a);
        	}
        }
        
        return conflicts;
	}
	
	@Override
	public Set<TimeBlock> getConflictingTimeBlocks(Long classId) {
		if (classId == null) return null;
		Class_ clazz = Class_DAO.getInstance().get(classId);
		if (clazz == null || clazz.isCancelled()) return null;
		Set<TimeBlock> conflicts = new TreeSet<TimeBlock>(new TimeBlockComparator());
		
		if (RoomAvailability.getInstance() != null && RoomAvailability.getInstance() instanceof DefaultRoomAvailabilityService) {
			EventDateMapping.Class2EventDateMap class2eventDateMap = EventDateMapping.getMapping(clazz.getManagingDept().getSessionId());
 			boolean changePast = ApplicationProperty.ClassAssignmentChangePastMeetings.isTrue();
 			boolean ignorePast = ApplicationProperty.ClassAssignmentIgnorePastMeetings.isTrue();
 			List<Meeting> meetings = null;
 			if (!changePast || ignorePast) {
 				Calendar cal = Calendar.getInstance(Localization.getJavaLocale());
 				cal.set(Calendar.HOUR_OF_DAY, 0);
 				cal.set(Calendar.MINUTE, 0);
 				cal.set(Calendar.SECOND, 0);
 				cal.set(Calendar.MILLISECOND, 0);
 				Date today = cal.getTime();
 				meetings = MeetingDAO.getInstance().getSession().createQuery(
 						"select distinct mx from ClassEvent e inner join e.meetings m, Meeting mx " +
 						"where e.clazz.uniqueId = :classId and mx.event.class != ClassEvent and m.approvalStatus = 1 and mx.approvalStatus = 1 and " +
 						"m.locationPermanentId = mx.locationPermanentId and m.meetingDate = mx.meetingDate and " +
 						"m.startPeriod < mx.stopPeriod and m.stopPeriod > mx.startPeriod and mx.meetingDate >= :today"
 						).setLong("classId", classId).setDate("today", today).setCacheable(true).list();
 			} else {
 				meetings = MeetingDAO.getInstance().getSession().createQuery(
 						"select distinct mx from ClassEvent e inner join e.meetings m, Meeting mx " +
 						"where e.clazz.uniqueId = :classId and mx.event.class != ClassEvent and m.approvalStatus = 1 and mx.approvalStatus = 1 and " +
 						"m.locationPermanentId = mx.locationPermanentId and m.meetingDate = mx.meetingDate and " +
 						"m.startPeriod < mx.stopPeriod and m.stopPeriod > mx.startPeriod"
 						).setLong("classId", classId).setCacheable(true).list();
 			}
			for (Meeting m: meetings) {
				MeetingTimeBlock block = new MeetingTimeBlock(m, class2eventDateMap);
	            if (block.getStartTime() != null)
	            	conflicts.add(block);
			}
			return conflicts;
		}

		Assignment assignment = getAssignment(clazz);
		if (assignment != null && assignment.getRooms() != null && !assignment.getRooms().isEmpty() && RoomAvailability.getInstance() != null) {
        	Date[] bounds = DatePattern.getBounds(clazz.getSessionId());
 			boolean changePast = ApplicationProperty.ClassAssignmentChangePastMeetings.isTrue();
 			boolean ignorePast = ApplicationProperty.ClassAssignmentIgnorePastMeetings.isTrue();
 			Calendar cal = Calendar.getInstance(Locale.US);
    		cal.setTime(new Date());
    		cal.set(Calendar.HOUR_OF_DAY, 0);
    		cal.set(Calendar.MINUTE, 0);
    		cal.set(Calendar.SECOND, 0);
    		cal.set(Calendar.MILLISECOND, 0);
    		Date today = cal.getTime();
    		ClassTimeInfo period = new ClassTimeInfo(assignment);
    		
			for (Location room : assignment.getRooms()) {
				if (!room.isIgnoreRoomCheck()) {
		    		Collection<TimeBlock> times = RoomAvailability.getInstance().getRoomAvailability(
		                    room.getUniqueId(),
		                    bounds[0], bounds[1], 
		                    RoomAvailabilityInterface.sClassType);
		    		if (times != null && !times.isEmpty()) {
		    			Collection<TimeBlock> timesToCheck = null;
		    			if (!changePast || ignorePast) {
		        			timesToCheck = new Vector();
		        			for (TimeBlock time: times) {
		        				if (!time.getEndTime().before(today))
		        					timesToCheck.add(time);
		        			}
		        		} else {
		        			timesToCheck = times;
		        		}
		        		List<TimeBlock> overlaps = period.allOverlaps(timesToCheck);
		        		if (overlaps != null)
		    				conflicts.addAll(overlaps);
		    		}
				}
    		}
        }
		
		return conflicts;
	}
}

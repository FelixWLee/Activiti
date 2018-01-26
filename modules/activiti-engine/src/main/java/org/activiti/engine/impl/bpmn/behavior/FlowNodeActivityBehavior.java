/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.bpmn.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.activiti.bpmn.model.Signal;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.impl.pvm.delegate.SignallableActivityBehavior;


/**
 * Superclass for all 'connectable' BPMN 2.0 process elements: tasks, gateways and events.
 * This means that any subclass can be the source or target of a sequenceflow.
 * 
 * Corresponds with the notion of the 'flownode' in BPMN 2.0.
 * 
 * @author Joram Barrez
 */
public abstract class FlowNodeActivityBehavior implements SignallableActivityBehavior {

  private static String FIRED_SIGNAL_EVENTS = "firedSignalEvents";
  
  protected BpmnActivityBehavior bpmnActivityBehavior = new BpmnActivityBehavior();

  /**
   * Default behaviour: just leave the activity with no extra functionality.
   */
  @Override
  public void execute(ActivityExecution execution) throws Exception {
    leave(execution);
  }
  
  /**
   * Default way of leaving a BPMN 2.0 activity: evaluate the conditions on the
   * outgoing sequence flow and take those that evaluate to true.
   */
  protected void leave(ActivityExecution execution) {
    bpmnActivityBehavior.performDefaultOutgoingBehavior(execution);
  }
  
  protected void leaveIgnoreConditions(ActivityExecution activityContext) {
    bpmnActivityBehavior.performIgnoreConditionsOutgoingBehavior(activityContext);
  }

  @Override
  public void signal(ActivityExecution execution, String signalName, Object signalData) throws Exception {
    // concrete activity behaviours that do accept signals should override this method;
    throw new ActivitiException("this activity doesn't accept signals");
  }

  /**
   * Register fired signals to handle race conditions within current transaction scope
   */
  @SuppressWarnings("unchecked")
  protected void registerFiredSignalEvent(ActivityExecution execution, Signal signal) {
    String signalScope = getSignalScope(execution, signal);
    
    CommandContext currentCommandContext = Context.getCommandContext();
    Map<String, List<String>> signalEvents = null;
    
    // register fired signals to handle race conditions within current transaction scope 
    if (currentCommandContext.getAttribute(FIRED_SIGNAL_EVENTS) == null ){
      signalEvents = new HashMap<String, List<String>>();
    } else {
      signalEvents = (Map<String, List<String>>) currentCommandContext.getAttribute(FIRED_SIGNAL_EVENTS);
    }
    
    List<String> signalNameList = signalEvents.get(signalScope);
    if (signalNameList == null) {
        signalNameList = new ArrayList<String>();
    }
    signalNameList.add(signal.getName());
    signalEvents.put(signalScope, signalNameList);

    Context.getCommandContext().addAttribute(FIRED_SIGNAL_EVENTS, signalEvents);
  }
  
  /**
   * Check if event has already been fired in the current transaction scope
   */
  @SuppressWarnings("unchecked")
  protected boolean isSignalEventAlreadyFired(ActivityExecution execution, EventSubscriptionEntity subscription) {

    if (isSignalEventSubscription(subscription)) {
      return false;
    }

    final String subscriptionScope = getEventSubscriptionScope(execution, subscription);
    
    Map<String, List<String>> signalEvents = (Map<String, List<String>>) Context.getCommandContext()
                                                                            .getAttribute(FIRED_SIGNAL_EVENTS);
    if (signalEvents != null) {
      List<String> signalNameList = signalEvents.get(subscriptionScope);
      return (signalNameList != null) && signalNameList.contains(subscription.getEventName());
    }
    return false;
  }

  protected boolean isSignalEventSubscription(EventSubscriptionEntity subscription) {
    return !"signal".equals(subscription.getEventType());
  }

  protected String getEventSubscriptionScope(ActivityExecution execution, EventSubscriptionEntity subscription) {
    String subscriptionScope = null;
    
    if (subscription.getConfiguration() != null) {
      Pattern pattern = Pattern.compile("\"scope\"\\s*:\\s*\"([^\"]+)\",?");
      Matcher matcher = pattern.matcher(subscription.getConfiguration());

      if (matcher.find()) {
        subscriptionScope = matcher.group(1);
      }
    }

    return getSignalExecutionScope(execution, subscriptionScope);
  }

  protected String getSignalScope(ActivityExecution execution, Signal signal) {
    return getSignalExecutionScope(execution, signal.getScope());

  }

  protected String getSignalExecutionScope(ActivityExecution execution, String signalScope) {
    return Signal.SCOPE_PROCESS_INSTANCE.equals(signalScope) 
              ? execution.getProcessInstanceId()
              : Signal.SCOPE_GLOBAL;

  }
  
  
}

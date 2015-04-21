/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.driver.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.behaviour.NextGroup;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.EthTypeCriterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.MplsCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupKey;

/**
 * Spring-open driver implementation for Dell hardware switches.
 */
public class SpringOpenTTPDell extends SpringOpenTTP {

    /* Table IDs to be used for Dell Open Segment Routers*/
    private static final int DELL_TABLE_VLAN = 17;
    private static final int DELL_TABLE_TMAC = 18;
    private static final int DELL_TABLE_IPV4_UNICAST = 30;
    private static final int DELL_TABLE_MPLS = 25;
    private static final int DELL_TABLE_ACL = 40;

    //TODO: Store this info in the distributed store.
    private MacAddress deviceTMac = null;

    public SpringOpenTTPDell() {
        super();
        vlanTableId = DELL_TABLE_VLAN;
        tmacTableId = DELL_TABLE_TMAC;
        ipv4UnicastTableId = DELL_TABLE_IPV4_UNICAST;
        mplsTableId = DELL_TABLE_MPLS;
        aclTableId = DELL_TABLE_ACL;
    }

    @Override
    protected void setTableMissEntries() {
        // No need to set table-miss-entries in Dell switches
        return;
    }

    @Override
    //Dell switches need ETH_DST based match condition in all IP table entries.
    //So this method overrides the default spring-open behavior and adds
    //ETH_DST match condition while pushing IP table flow rules
    protected Collection<FlowRule> processSpecific(ForwardingObjective fwd) {
        log.debug("Processing specific");
        TrafficSelector selector = fwd.selector();
        EthTypeCriterion ethType = (EthTypeCriterion) selector
                .getCriterion(Criterion.Type.ETH_TYPE);
        if ((ethType == null) ||
                ((((short) ethType.ethType()) != Ethernet.TYPE_IPV4) &&
                (((short) ethType.ethType()) != Ethernet.MPLS_UNICAST))) {
            log.debug("processSpecific: Unsupported "
                    + "forwarding objective criteraia");
            fail(fwd, ObjectiveError.UNSUPPORTED);
            return Collections.emptySet();
        }

        TrafficSelector.Builder filteredSelectorBuilder =
                DefaultTrafficSelector.builder();
        int forTableId = -1;
        if (((short) ethType.ethType()) == Ethernet.TYPE_IPV4) {
            if (deviceTMac == null) {
                log.debug("processSpecific: ETH_DST filtering "
                        + "objective is not set which is required "
                        + "before sending a IPv4 forwarding objective");
                //TODO: Map the error to more appropriate error code.
                fail(fwd, ObjectiveError.DEVICEMISSING);
                return Collections.emptySet();
            }
            filteredSelectorBuilder = filteredSelectorBuilder
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchEthDst(deviceTMac)
                .matchIPDst(((IPCriterion) selector
                        .getCriterion(Criterion.Type.IPV4_DST))
                        .ip());
            forTableId = ipv4UnicastTableId;
            log.debug("processing IPv4 specific forwarding objective");
        } else {
            filteredSelectorBuilder = filteredSelectorBuilder
                .matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(((MplsCriterion)
                   selector.getCriterion(Criterion.Type.MPLS_LABEL)).label());
            //TODO: Add Match for BoS
            //if (selector.getCriterion(Criterion.Type.MPLS_BOS) != null) {
            //}
            forTableId = mplsTableId;
            log.debug("processing MPLS specific forwarding objective");
        }

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment
                .builder();
        if (fwd.treatment() != null) {
            for (Instruction i : fwd.treatment().allInstructions()) {
                treatmentBuilder.add(i);
            }
        }

        if (fwd.nextId() != null) {
            NextGroup next = flowObjectiveStore.getNextGroup(fwd.nextId());

            if (next != null) {
                GroupKey key = appKryo.deserialize(next.data());

                Group group = groupService.getGroup(deviceId, key);

                if (group == null) {
                    log.warn("The group left!");
                    fail(fwd, ObjectiveError.GROUPMISSING);
                    return Collections.emptySet();
                }
                treatmentBuilder.group(group.id());
                log.debug("Adding OUTGROUP action");
            }
        }

        TrafficSelector filteredSelector = filteredSelectorBuilder.build();
        TrafficTreatment treatment = treatmentBuilder.transition(aclTableId)
                .build();

        FlowRule.Builder ruleBuilder = DefaultFlowRule.builder()
                .fromApp(fwd.appId()).withPriority(fwd.priority())
                .forDevice(deviceId).withSelector(filteredSelector)
                .withTreatment(treatment);

        if (fwd.permanent()) {
            ruleBuilder.makePermanent();
        } else {
            ruleBuilder.makeTemporary(fwd.timeout());
        }

        ruleBuilder.forTable(forTableId);
        return Collections.singletonList(ruleBuilder.build());

    }

    @Override
    //Dell switches need ETH_DST based match condition in all IP table entries.
    //So while processing the ETH_DST based filtering objective, store
    //the device MAC to be used locally to use it while pushing the IP rules.
    protected List<FlowRule> processEthDstFilter(Criterion c,
                                                 FilteringObjective filt,
                                                 ApplicationId applicationId) {
        List<FlowRule> rules = new ArrayList<FlowRule>();
        EthCriterion e = (EthCriterion) c;
        TrafficSelector.Builder selectorIp = DefaultTrafficSelector
                .builder();
        TrafficTreatment.Builder treatmentIp = DefaultTrafficTreatment
                .builder();

        // Store device termination Mac to be used in IP flow entries
        deviceTMac = e.mac();

        selectorIp.matchEthDst(e.mac());
        selectorIp.matchEthType(Ethernet.TYPE_IPV4);
        treatmentIp.transition(ipv4UnicastTableId);
        FlowRule ruleIp = DefaultFlowRule.builder().forDevice(deviceId)
                .withSelector(selectorIp.build())
                .withTreatment(treatmentIp.build())
                .withPriority(filt.priority()).fromApp(applicationId)
                .makePermanent().forTable(tmacTableId).build();
        log.debug("adding IP ETH rule for MAC: {}", e.mac());
        rules.add(ruleIp);

        TrafficSelector.Builder selectorMpls = DefaultTrafficSelector
                .builder();
        TrafficTreatment.Builder treatmentMpls = DefaultTrafficTreatment
                .builder();
        selectorMpls.matchEthDst(e.mac());
        selectorMpls.matchEthType(Ethernet.MPLS_UNICAST);
        treatmentMpls.transition(mplsTableId);
        FlowRule ruleMpls = DefaultFlowRule.builder()
                .forDevice(deviceId).withSelector(selectorMpls.build())
                .withTreatment(treatmentMpls.build())
                .withPriority(filt.priority()).fromApp(applicationId)
                .makePermanent().forTable(tmacTableId).build();
        log.debug("adding MPLS ETH rule for MAC: {}", e.mac());
        rules.add(ruleMpls);

        return rules;
    }

}
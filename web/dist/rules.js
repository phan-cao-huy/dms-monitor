export function componentKey(rule){
  return JSON.stringify([rule.metric_type,rule.metric_type==='SERVICE'?(rule.service_name||''):rule.metric_type==='PROCESS_MISSING'?(rule.process_name||''):'']);
}
export function effectiveRules(rules,device){
  const chosen=new Map();
  for(const rule of rules){
    if(!rule.is_active)continue;
    const priority=rule.device_id!=null?(String(rule.device_id)===String(device.device_id)?3:0):rule.group_id!=null?(String(rule.group_id)===String(device.group_id)?2:0):(String(rule.workspace_id)===String(device.org_id)?1:0);
    if(!priority)continue;
    const key=componentKey(rule),old=chosen.get(key);
    if(!old||priority>old.priority||(priority===old.priority&&rule.rule_id>old.rule.rule_id))chosen.set(key,{rule,priority});
  }
  return [...chosen.values()].map(item=>item.rule);
}
export function duplicateRule(rules,candidate){
  return rules.find(rule=>rule.is_active&&rule.rule_id!==candidate.rule_id&&componentKey(rule)===componentKey(candidate)&&
    (candidate.device_id!=null?rule.device_id!=null&&String(rule.device_id)===String(candidate.device_id):candidate.group_id!=null&&rule.group_id!=null&&String(rule.group_id)===String(candidate.group_id)));
}

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {analyticsView,labelsPanel,labelChips,labelOptions,matchesLabel,rangeHours,fillTimeline} from '../dist/metrics.js';
import {chart} from '../dist/ui.js';

const panel=(...args)=>args.join('');

test('labels filter preserves values containing colon and escapes HTML',()=>{
  const d={labels:{env:'prod:blue',name:'<script>'}};
  assert.equal(matchesLabel(d,'env:prod:blue'),true);
  assert.equal(matchesLabel(d,'env:prod'),false);
  assert.deepEqual(labelOptions([d,d]),[['env:prod:blue','env:prod:blue'],['name:<script>','name:<script>']]);
  assert.doesNotMatch(labelChips(d.labels),/<script>/);
  assert.doesNotMatch(labelsPanel(d,true,panel),/<script>/);
  assert.doesNotMatch(labelsPanel(d,false,panel),/data-action="add-label"/);
});

test('analytics view renders only scoped server series and escapes names',()=>{
  const state={devices:[],analysisData:{metric:'cpu_usage',agg:'avg',resolution:'minute',step_seconds:60,series:[{key:1,name:'<img>',points:[{timestamp:'2026-10-28T00:00:00Z',value:50,sample_count:2}]}]}};
  const html=analyticsView(state,panel,panel);
  assert.match(html,/Tầng phút/);
  assert.doesNotMatch(html,/<img>|undefined|NaN/);
  assert.equal(rangeHours('90d'),2160);
});

test('missing time buckets stay empty rather than drawing lines across gaps',()=>{
  const rows=fillTimeline([{created_at:'2026-10-28T00:00:00Z',cpu_usage:10},{created_at:'2026-10-28T00:02:00Z',cpu_usage:90}],
    '2026-10-28T00:00:00Z','2026-10-28T00:03:00Z',60);
  assert.equal(rows.length,3);
  assert.equal(rows[1].cpu_usage,undefined);
  const html=chart(rows,{resolution:'minute',stepSeconds:60});
  assert.match(html,/Tầng phút/);
  assert.match(html,/Mỗi điểm 60 giây/);
});

test('chart mean uses sample counts instead of averaging bucket averages',()=>{
  const html=chart([{created_at:'2026-10-28T00:00:00Z',cpu_usage:0,cpu_usage_samples:1},
    {created_at:'2026-10-28T00:01:00Z',cpu_usage:100,cpu_usage_samples:3}],{ram:false});
  assert.match(html,/TB có trọng số/);
  assert.match(html,/>75%/);
});

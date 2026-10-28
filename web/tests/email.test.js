import {test} from 'node:test';
import assert from 'node:assert/strict';
import {emailForm,emailPayload,emailHistory,emailView} from '../dist/email.js';

test('email editor retains stored password by omission and defaults to Gmail TLS',()=>{
  const html=emailForm({name:'Example',config:{host:'smtp.gmail.com',password:'DO_NOT_RENDER'},enabled:true});
  assert.doesNotMatch(html,/DO_NOT_RENDER/);
  assert.match(html,/smtp.gmail.com/);
  assert.match(html,/STARTTLS/);
  const form=new Map(Object.entries({name:' Test ',enabled:'true',host:'smtp.gmail.com',port:'587',username:'a@example.com',from_email:'a@example.com',recipients:'b@example.com\nb@example.com;c@example.com',language:'en',password:''}));
  const payload=emailPayload(form);
  assert.equal(payload.config.port,587);
  assert.equal('password' in payload.config,false);
  assert.deepEqual(payload.config.recipients,['b@example.com','c@example.com']);
});

test('email views escape server data and support both languages',()=>{
  const channel={channel_id:1,name:'<script>',enabled:true,config:{host:'smtp.gmail.com',port:587,recipients:['<img>'],language:'vi'}};
  const vi=emailView({alertLanguage:'vi',emailChannels:[channel]},(...args)=>args.join(''));
  const en=emailView({alertLanguage:'en',emailChannels:[channel]},(...args)=>args.join(''));
  assert.doesNotMatch(vi,/<script>|<img>/);
  assert.match(vi,/Gửi thử/);
  assert.match(en,/Send test/);
  assert.match(emailHistory([{alert_id:1,status:'FAILED',attempts:3,last_error:'SMTP_AUTH_FAILED'}],true),/SMTP authentication failed/);
});

// Postman Collection v2.1 的集合级 Pre-request Script；只实现现有公开协议。
// 凭据从当前环境/集合变量读取，禁止在控制台输出密钥或签名原文。
const CryptoJS = require('crypto-js');
const resolve = value => pm.variables.replaceIn(String(value));
const required = name => {
    const value = resolve(pm.variables.get(name) || '').trim();
    if (!value || /\{\{[^}]+\}\}/.test(value)) {
        throw new Error('请先配置测试变量：' + name);
    }
    return value;
};
const clientId = required('clientId');
const keyId = required('keyId');
const secret = required('hmacSecret');
if (!/^[A-Za-z0-9+/]{43}=$/.test(secret)) {
    throw new Error('hmacSecret 必须是32字节随机数的标准Base64');
}
const key = CryptoJS.enc.Base64.parse(secret);
if (key.sigBytes !== 32 || CryptoJS.enc.Base64.stringify(key) !== secret) {
    throw new Error('hmacSecret 不是有效的32字节Base64密钥');
}

// 先展开变量，再对最终发送的原始正文签名；不得签完再格式化JSON。
const url = resolve(pm.request.url.toString());
if (/\{\{[^}]+\}\}/.test(url)) throw new Error('URL中仍有未填写的变量');
const match = /^(https?:\/\/[^/?#]+)(\/[^?#]*)?(?:\?([^#]*))?$/.exec(url);
if (!match) throw new Error('请求URL格式无效');
const path = match[2] || '/';
const method = pm.request.method.toUpperCase();
const body = pm.request.body && pm.request.body.mode === 'raw'
    ? resolve(pm.request.body.raw || '') : '';
if (/\{\{[^}]+\}\}/.test(body)) throw new Error('正文中仍有未填写的变量');
if (method === 'GET' && body) throw new Error('GET请求正文必须为空');
if (pm.request.body && pm.request.body.mode === 'raw') {
    pm.request.body.update({ mode: 'raw', raw: body });
}

const encode = value => encodeURIComponent(value).replace(/[!'()*]/g,
    char => '%' + char.charCodeAt(0).toString(16).toUpperCase());
const names = new Set();
const entries = (match[3] || '').split('&').filter(Boolean).map(item => {
    if (item.includes('+')) throw new Error('查询参数空格请使用%20，不能使用+');
    const at = item.indexOf('=');
    const name = decodeURIComponent(at < 0 ? item : item.slice(0, at));
    const value = decodeURIComponent(at < 0 ? '' : item.slice(at + 1));
    if (names.has(name)) throw new Error('重复查询参数：' + name);
    names.add(name);
    return [encode(name), encode(value)];
});
entries.sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1
    : a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0);
const query = entries.map(entry => entry.join('=')).join('&');
// 规范化查询同时用于实际发送，避免工具自动编码与签名内容不一致。
pm.request.url.update(match[1] + path + (query ? '?' + query : ''));

let idempotencyKey = '';
if (method === 'POST' && path === '/open-api/v1/purchase-drafts') {
    idempotencyKey = required('idempotencyKey');
    if (!/^[A-Za-z0-9._:-]{16,160}$/.test(idempotencyKey)) {
        throw new Error('idempotencyKey须为16—160位字母/数字/._:-');
    }
    pm.request.headers.upsert({ key: 'Idempotency-Key', value: idempotencyKey });
} else {
    pm.request.headers.remove('Idempotency-Key');
}
const timestamp = Math.floor(Date.now() / 1000).toString();
const nonce = pm.variables.replaceIn('{{$guid}}');
const requestId = pm.variables.replaceIn('{{$guid}}');
const hash = CryptoJS.SHA256(CryptoJS.enc.Utf8.parse(body)).toString(CryptoJS.enc.Hex);
const canonical = [method, path, query, clientId, keyId, timestamp,
    nonce, requestId, idempotencyKey, hash].join('\n');
const signature = CryptoJS.HmacSHA256(CryptoJS.enc.Utf8.parse(canonical), key)
    .toString(CryptoJS.enc.Base64);
const headers = {
    'X-Client-Id': clientId, 'X-Key-Id': keyId, 'X-Timestamp': timestamp,
    'X-Nonce': nonce, 'X-Request-Id': requestId, 'X-Content-SHA256': hash,
    'X-Signature': signature, 'Accept': 'application/json'
};
if (method === 'POST') headers['Content-Type'] = 'application/json; charset=utf-8';
Object.entries(headers).forEach(([key, value]) => pm.request.headers.upsert({ key, value }));

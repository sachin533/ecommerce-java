async function handle(res) {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.detail || body.message || 'Request failed');
  return body;
}
const q = (p) => new URLSearchParams(p).toString();

export const api = {
  users: () => fetch('/api/users').then(handle),
  products: ({ category = '', tag = '', search = '', page = 1, limit = 20 } = {}) =>
    fetch(`/api/products?${q({ category, tag, search, page, limit })}`).then(handle),
  createProduct: (p) =>
    fetch('/api/products', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(p) }).then(handle),
  updateProduct: (id, p) =>
    fetch(`/api/products/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(p) }).then(handle),
  placeOrder: (user_id, items) =>
    fetch('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ user_id, items }) }).then(handle),
  order: (id) => fetch(`/api/orders/${id}`).then(handle),
  setStatus: (id, status) =>
    fetch(`/api/orders/${id}/status`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status }) }).then(handle),
  searchOrders: (payload) =>
    fetch('/api/search/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(handle),
};

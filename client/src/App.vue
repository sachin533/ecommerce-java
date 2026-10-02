<script setup>
import { ref, computed, onMounted, onUpdated, onUnmounted } from 'vue';
import { api } from './api/client.js';

const view = ref('store');
const users = ref([]);
const currentUserId = ref(null);
const products = ref([]);
const categories = ref([]);
const categoryFilter = ref('');
const productSearch = ref('');
const productPage = ref(1);
const productLimit = 20;
const productTotal = ref(0);
const productHasNext = ref(false);
const productLoading = ref(false);
const productLoadingMore = ref(false);
const productError = ref('');
let productReq = 0;
let searchTimer = null;
let observer = null;
const sentinel = ref(null);
const PLACEHOLDER_IMG = 'https://picsum.photos/seed/placeholder/400/300';

function productImg(p) {
  return p.imageUrl || `https://picsum.photos/seed/${p.id}/400/300`;
}
function onImgError(event) {
  event.target.src = PLACEHOLDER_IMG;
}
const cart = ref({});
const checkoutMsg = ref('');
const checkoutErr = ref('');

// Screen 3 state
const sq = ref('');
const sStatuses = ref([]);
const sFrom = ref('');
const sTo = ref('');
const sMin = ref('');
const sMax = ref('');
const sResult = ref(null);
const sLoading = ref(false);
let sTimer = null;

// Screen 4 state
const orderDetail = ref(null);

// Screen 5 state
const editing = ref(null);
const editJson = ref('');
const editMsg = ref('');
const catalogProducts = ref([]);

async function loadCatalog() {
  // Admin view needs the full list: walk all pages (one-time, admin only).
  const all = [];
  let page = 1;
  for (;;) {
    const res = await api.products({ page, limit: 100 });
    all.push(...(res.products || []));
    if (!res.hasNextPage) break;
    page += 1;
  }
  catalogProducts.value = all;
}

const cartCount = computed(() => Object.values(cart.value).reduce((a, b) => a + b, 0));
const cartLines = computed(() =>
  Object.entries(cart.value).map(([pid, qty]) => {
    const p = products.value.find(x => x.id === pid);
    return { pid, qty, title: p?.title || pid, price: p?.price || 0 };
  }));
const cartTotal = computed(() =>
  Math.round(cartLines.value.reduce((a, l) => a + l.qty * l.price, 0) * 100) / 100);

async function loadUsers() {
  users.value = await api.users();
  if (users.value.length && !currentUserId.value) currentUserId.value = users.value[0].id;
}
async function loadProducts({ append = false } = {}) {
  const seq = ++productReq;
  if (append) productLoadingMore.value = true;
  else productLoading.value = true;
  productError.value = '';
  try {
    const res = await api.products({
      category: categoryFilter.value,
      search: productSearch.value.trim(),
      page: productPage.value,
      limit: productLimit,
    });
    if (seq !== productReq) return; // stale response
    const items = res.products || res;
    products.value = append ? [...products.value, ...items] : items;
    productTotal.value = res.totalProducts ?? items.length;
    productHasNext.value = res.hasNextPage ?? false;
  } catch (e) {
    if (seq !== productReq) return;
    productError.value = e.message || 'Failed to load products.';
  } finally {
    if (seq === productReq) {
      productLoading.value = false;
      productLoadingMore.value = false;
    }
  }
}
function resetAndLoad() {
  productPage.value = 1;
  products.value = [];
  loadProducts();
}
function onSearchInput() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(resetAndLoad, 300);
}
function loadMore() {
  if (productLoading.value || productLoadingMore.value || !productHasNext.value) return;
  productPage.value += 1;
  loadProducts({ append: true });
}
function setupInfiniteScroll() {
  if (observer) observer.disconnect();
  observer = new IntersectionObserver(
    (entries) => { if (entries[0].isIntersecting) loadMore(); },
    { rootMargin: '300px' });
  if (sentinel.value) observer.observe(sentinel.value);
}
async function loadCategories() {
  categories.value = await fetch('/api/products/categories').then(r => r.json()).catch(() => []);
}
function setCategory(c) {
  categoryFilter.value = c;
  resetAndLoad();
}
function addToCart(p) {
  cart.value[p.id] = (cart.value[p.id] || 0) + 1;
}
async function placeOrder() {
  checkoutMsg.value = '';
  checkoutErr.value = '';
  try {
    const items = Object.entries(cart.value).map(([product_id, quantity]) => ({ product_id, quantity }));
    const order = await api.placeOrder(currentUserId.value, items);
    cart.value = {};
    checkoutMsg.value = `Order #${order.id} committed: $${order.total_amount} (${order.items.length} lines).`;
  } catch (e) {
    checkoutErr.value = e.message;
  }
}

// ---- Screen 3 ----
function runSearch() {
  clearTimeout(sTimer);
  sTimer = setTimeout(doSearch, 300);
}
async function doSearch() {
  sLoading.value = true;
  try {
    sResult.value = await api.searchOrders({
      q: sq.value,
      statuses: sStatuses.value,
      date_from: sFrom.value || null,
      date_to: sTo.value || null,
      min_total: sMin.value === '' ? null : Number(sMin.value),
      max_total: sMax.value === '' ? null : Number(sMax.value),
      page: 1,
      page_size: 20,
    });
  } catch (e) {
    sResult.value = { error: e.message, items: [], aggs: { total_revenue: 0, by_status: {} }, total: 0 };
  } finally {
    sLoading.value = false;
  }
}
function toggleStatus(s) {
  const i = sStatuses.value.indexOf(s);
  if (i >= 0) sStatuses.value.splice(i, 1);
  else sStatuses.value.push(s);
  doSearch();
}

// ---- Screen 4 ----
async function openOrder(id) {
  orderDetail.value = await api.order(id);
  view.value = 'order';
}
async function changeStatus(status) {
  orderDetail.value = await api.setStatus(orderDetail.value.id, status);
  doSearch();
}

// ---- Screen 5 ----
function startEdit(p) {
  editing.value = p ? { ...p } : { sku: '', title: '', description: '', price: 0, category: 'office', tags: [], attributes: {}, variants: [{}], active: true };
  editJson.value = JSON.stringify(
    { tags: editing.value.tags, attributes: editing.value.attributes, variants: editing.value.variants }, null, 2);
  editMsg.value = '';
}
async function saveProduct() {
  let extra = {};
  try {
    extra = JSON.parse(editJson.value || '{}');
  } catch {
    editMsg.value = 'Tags/attributes/variants JSON is invalid.';
    return;
  }
  const payload = { ...editing.value, tags: extra.tags || [], attributes: extra.attributes || {}, variants: extra.variants || [] };
  try {
    if (payload.id) await api.updateProduct(payload.id, payload);
    else await api.createProduct(payload);
    editing.value = null;
    editMsg.value = '';
    await loadCatalog();
    await loadProducts();
  } catch (e) {
    editMsg.value = e.message;
  }
}

onMounted(async () => {
  await loadUsers();
  await loadProducts();
  await loadCategories();
  await doSearch();
  setupInfiniteScroll();
});
onUpdated(() => {
  if (sentinel.value) setupInfiniteScroll();
});
onUnmounted(() => {
  if (observer) observer.disconnect();
  clearTimeout(searchTimer);
});
</script>

<template>
  <div class="app">
    <header>
      <h1>ShopSmart POC</h1>
      <nav>
        <button :class="{ active: view === 'store' }" @click="view = 'store'">Storefront</button>
        <button :class="{ active: view === 'checkout' }" @click="view = 'checkout'">Checkout ({{ cartCount }})</button>
        <button :class="{ active: view === 'search' }" @click="view = 'search'; doSearch()">Admin Search</button>
        <button :class="{ active: view === 'catalog' }" @click="view = 'catalog'; loadCatalog()">Catalog Admin</button>
      </nav>
      <label class="login">Log In As
        <select v-model.number="currentUserId">
          <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
        </select>
      </label>
    </header>

    <!-- SCREEN 1: storefront (MongoDB) -->
    <section v-if="view === 'store'">
      <div class="toolbar">
        <input
          v-model="productSearch"
          @input="onSearchInput"
          type="search"
          placeholder="Search products…"
          aria-label="Search products"
          class="searchbox"
        />
      </div>
      <div class="toolbar">
        <button :class="{ active: !categoryFilter }" @click="setCategory('')">All</button>
        <button v-for="c in categories" :key="c" :class="{ active: categoryFilter === c }" @click="setCategory(c)">{{ c }}</button>
      </div>
      <p v-if="productLoading" class="muted">Loading products…</p>
      <p v-else-if="productError" class="err">{{ productError }}</p>
      <div v-else class="grid">
        <article v-for="p in products" :key="p.id" class="card">
          <img :src="productImg(p)" @error="onImgError" :alt="p.title" loading="lazy" class="thumb" />
          <h3>{{ p.title }}</h3>
          <p class="muted">{{ p.description }}</p>
          <p><strong>${{ p.price }}</strong> <span class="muted">{{ p.category }}</span></p>
          <p class="muted small">Tags: {{ (p.tags || []).join(', ') }} · Color: {{ p.attributes?.color || '—' }}</p>
          <button @click="addToCart(p)">Add to Cart</button>
        </article>
      </div>
      <p v-if="!productLoading && !productError && !products.length" class="muted">
        No products found{{ productSearch ? ` for “${productSearch}”` : '' }}.
      </p>
      <div ref="sentinel"></div>
      <p v-if="productLoadingMore" class="muted">Loading more…</p>
      <p v-else-if="!productLoading && products.length" class="muted small">
        Showing {{ products.length }} of {{ productTotal }}{{ productHasNext ? '' : ' — end of list' }}
      </p>
    </section>

    <!-- SCREEN 2: checkout (PostgreSQL transaction) -->
    <section v-if="view === 'checkout'">
      <h2>Checkout</h2>
      <p v-if="checkoutMsg" class="ok">{{ checkoutMsg }}</p>
      <p v-if="checkoutErr" class="err">{{ checkoutErr }}</p>
      <table v-if="cartLines.length">
        <thead><tr><th>Item</th><th>Qty</th><th>Unit</th><th>Line</th></tr></thead>
        <tbody>
          <tr v-for="l in cartLines" :key="l.pid">
            <td>{{ l.title }}</td><td>{{ l.qty }}</td><td>${{ l.price }}</td>
            <td>${{ Math.round(l.qty * l.price * 100) / 100 }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">Cart is empty — add products from the Storefront.</p>
      <h3>Total: ${{ cartTotal }}</h3>
      <button :disabled="!cartLines.length" @click="placeOrder">Place Order</button>
    </section>

    <!-- SCREEN 3: admin search (Elasticsearch ONLY) -->
    <section v-if="view === 'search'">
      <h2>Admin Search Dashboard <span class="badge">Elasticsearch</span></h2>
      <input v-model="sq" @input="runSearch" placeholder='Omni-search: try "Wireless"…' class="omni" />
      <div class="search-layout">
        <aside>
          <h4>Status</h4>
          <label v-for="s in ['PENDING', 'PROCESSING', 'SHIPPED']" :key="s">
            <input type="checkbox" :checked="sStatuses.includes(s)" @change="toggleStatus(s)" /> {{ s }}
          </label>
          <h4>Date range</h4>
          <input type="date" v-model="sFrom" @change="doSearch" />
          <input type="date" v-model="sTo" @change="doSearch" />
          <h4>Total range</h4>
          <input type="number" v-model="sMin" @change="doSearch" placeholder="min $" />
          <input type="number" v-model="sMax" @change="doSearch" placeholder="max $" />
        </aside>
        <div>
          <div v-if="sResult" class="kpis">
            <div class="kpi">Revenue<strong>${{ Math.round((sResult.aggs?.total_revenue || 0) * 100) / 100 }}</strong></div>
            <div class="kpi" v-for="(n, st) in (sResult.aggs?.by_status || {})" :key="st">{{ st }}<strong>{{ n }}</strong></div>
          </div>
          <p v-if="sLoading" class="muted">Searching…</p>
          <p v-if="sResult?.error" class="err">{{ sResult.error }}</p>
          <table v-if="sResult?.items?.length">
            <thead><tr><th>Order</th><th>Date</th><th>Customer</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>
              <tr v-for="o in sResult.items" :key="o.order_id">
                <td><a href="#" @click.prevent="openOrder(o.order_id)">#{{ o.order_id }}</a></td>
                <td>{{ (o.order_date || '').slice(0, 10) }}</td>
                <td>{{ o.customer?.name }}</td>
                <td>${{ o.total_amount }}</td>
                <td>{{ o.status }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="sResult" class="muted">No orders match ({{ sResult.total }} total).</p>
        </div>
      </div>
    </section>

    <!-- SCREEN 4: order details (PostgreSQL source of truth) -->
    <section v-if="view === 'order' && orderDetail">
      <button class="secondary" @click="view = 'search'">← Back to search</button>
      <h2>Order #{{ orderDetail.id }}</h2>
      <p>{{ orderDetail.customer_name }} ({{ orderDetail.customer_email }}) ·
        {{ (orderDetail.order_date || '').slice(0, 16).replace('T', ' ') }} ·
        ES in sync: <strong>{{ orderDetail.es_in_sync ? 'yes' : 'NO — wait ~15s for poller' }}</strong></p>
      <table>
        <thead><tr><th>Title (snapshot)</th><th>Qty</th><th>Unit</th><th>Line</th></tr></thead>
        <tbody>
          <tr v-for="i in orderDetail.items" :key="i.id">
            <td>{{ i.title }}</td><td>{{ i.quantity }}</td><td>${{ i.unit_price }}</td>
            <td>${{ Math.round(i.quantity * i.unit_price * 100) / 100 }}</td>
          </tr>
        </tbody>
      </table>
      <h3>Total: ${{ orderDetail.total_amount }} · Status: {{ orderDetail.status }}</h3>
      <label>Set status
        <select :value="orderDetail.status" @change="changeStatus($event.target.value)">
          <option>PENDING</option><option>PROCESSING</option><option>SHIPPED</option>
        </select>
      </label>
    </section>

    <!-- SCREEN 5: catalog admin (MongoDB only) -->
    <section v-if="view === 'catalog'">
      <h2>Catalog Admin <span class="badge">MongoDB</span></h2>
      <button @click="startEdit(null)">+ New product</button>
      <div v-if="editing" class="card form">
        <h3>{{ editing.id ? 'Edit' : 'New' }} product</h3>
        <p v-if="editMsg" class="err">{{ editMsg }}</p>
        <label>SKU <input v-model="editing.sku" /></label>
        <label>Title <input v-model="editing.title" /></label>
        <label>Description <input v-model="editing.description" /></label>
        <label>Price <input v-model.number="editing.price" type="number" step="0.01" /></label>
        <label>Category <input v-model="editing.category" /></label>
        <label>Active <input v-model="editing.active" type="checkbox" /></label>
        <label>tags / attributes / variants (JSON)
          <textarea v-model="editJson" rows="8"></textarea>
        </label>
        <button @click="saveProduct">Save</button>
        <button class="secondary" @click="editing = null">Cancel</button>
      </div>
      <table>
        <thead><tr><th>Title</th><th>SKU</th><th>Price</th><th>Category</th><th>Active</th><th></th></tr></thead>
        <tbody>
          <tr v-for="p in catalogProducts" :key="p.id">
            <td>{{ p.title }}</td><td>{{ p.sku }}</td><td>${{ p.price }}</td>
            <td>{{ p.category }}</td><td>{{ p.active ? 'yes' : 'no' }}</td>
            <td><button class="small" @click="startEdit(p)">Edit</button></td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style>
* { box-sizing: border-box; }
body { font-family: system-ui, sans-serif; margin: 0; background: #f4f6fa; color: #1c2430; }
.app { max-width: 1200px; margin: 0 auto; padding: 20px; }
header { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; margin-bottom: 18px; }
header h1 { margin: 0 12px 0 0; font-size: 26px; }
nav { display: flex; gap: 8px; }
nav button.active { background: #172033; color: #fff; }
.login { margin-left: auto; display: flex; gap: 8px; align-items: center; }
button { border: 0; border-radius: 8px; padding: 9px 13px; background: #4255d9; color: #fff; font-weight: 700; cursor: pointer; }
button.secondary, nav button { background: #e9edf4; color: #293449; }
button.small { padding: 6px 9px; font-size: 12px; }
button:disabled { opacity: .55; cursor: not-allowed; }
input, select, textarea { border: 1px solid #d5dce6; border-radius: 8px; padding: 9px 10px; font: inherit; background: #fff; }
table { width: 100%; border-collapse: collapse; background: #fff; margin: 12px 0; }
th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid #edf0f4; }
th { background: #fafbfc; font-size: 12px; text-transform: uppercase; color: #566276; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.card { background: #fff; border: 1px solid #e2e7ef; border-radius: 12px; padding: 14px; }
.card h3 { margin: 0 0 6px; }
.thumb { width: 100%; height: 150px; object-fit: cover; border-radius: 8px; margin-bottom: 8px; background: #eef1f6; }
.searchbox { flex: 1; min-width: 220px; }
.muted { color: #637083; } .small { font-size: 12px; }
.ok { background: #e8f7ed; color: #287441; padding: 10px 12px; border-radius: 8px; }
.err { background: #fdebed; color: #a52d3a; padding: 10px 12px; border-radius: 8px; }
.badge { font-size: 11px; background: #172033; color: #fff; border-radius: 999px; padding: 3px 10px; vertical-align: middle; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar button.active { background: #172033; color: #fff; }
.omni { width: 100%; font-size: 16px; margin-bottom: 12px; }
.search-layout { display: grid; grid-template-columns: 220px 1fr; gap: 16px; align-items: start; }
.search-layout aside { background: #fff; border: 1px solid #e2e7ef; border-radius: 12px; padding: 12px; display: grid; gap: 8px; }
.search-layout aside h4 { margin: 6px 0 2px; }
.kpis { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.kpi { background: #172033; color: #fff; border-radius: 10px; padding: 10px 14px; display: grid; font-size: 12px; }
.kpi strong { font-size: 18px; }
.form label { display: grid; gap: 4px; margin: 8px 0; max-width: 560px; }
.form textarea { font-family: monospace; }
a { color: #4255d9; }
</style>

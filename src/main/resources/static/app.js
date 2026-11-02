// Progressive enhancement only. Every booking action also works without JavaScript.
function formatTimes(root = document) {
  root.querySelectorAll('time[datetime]').forEach(el => {
    const date = new Date(el.dateTime);
    if (!Number.isNaN(date.valueOf())) {
      el.textContent = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
    }
  });
}
formatTimes();
document.querySelectorAll('form').forEach(form => form.addEventListener('submit', () => {
  form.setAttribute('aria-busy', 'true');
  form.querySelectorAll('button[type="submit"], button:not([type])').forEach(button => { button.disabled = true; button.textContent = 'Please wait…'; });
}));
// A changed booking intent gets a new key. Unchanged network retries retain the old key.
const bookingForm = document.querySelector('#booking-form');
document.querySelectorAll('[name="slot_id"], #name, #email').forEach(input => input.addEventListener('change', () => {
  if (window.crypto?.randomUUID) bookingForm.elements.intent.value = crypto.randomUUID();
}));
window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
document.addEventListener('click', async event => {
  const link = event.target.closest('.load-more');
  if (!link) return;
  event.preventDefault();
  if (link.getAttribute('aria-disabled') === 'true') return;
  link.setAttribute('aria-disabled', 'true'); link.textContent = 'Loading…';
  const status = document.querySelector('.history-status');
  try {
    const response = await fetch(link.href, { headers: { Accept: 'text/html' } });
    if (!response.ok) throw new Error();
    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
    const list = page.querySelector('.history-list');
    if (!list) throw new Error();
    list.querySelectorAll('li').forEach(row => document.querySelector('.history-list').append(row));
    formatTimes(document.querySelector('#history'));
    const next = page.querySelector('.load-more');
    if (next) { link.href = next.href; link.textContent = 'Load more'; link.removeAttribute('aria-disabled'); }
    else link.remove();
    status.textContent = next ? 'More bookings loaded.' : 'All bookings loaded.';
  } catch {
    status.textContent = 'Could not load bookings. Please try again.';
    link.textContent = 'Try again'; link.removeAttribute('aria-disabled');
  }
});
const alert = document.querySelector('[role="alert"]');
if (alert) alert.focus();

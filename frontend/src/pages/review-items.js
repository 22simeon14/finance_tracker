/**
 * Main Responsibility: Client-side Items card for the review page.
 *
 * Owns line-item list state (description, qty, unit price, amount, category),
 * optgroup category selects, add/remove rows, lines-vs-receipt total note, and
 * per-row qty×unit≠amount warnings. linesForApprove() builds the JSON list
 * Approve posts. Used only by review.js.
 */

/** Local counter so each in-memory line has a stable DOM key before save. */
let nextLineKey = 1;

/**
 * Wire the Items section DOM. Call setCategories once categories load, then
 * loadFromExtraction whenever the document / extraction is refreshed.
 */
export function createReviewItemsEditor({
  listEl,
  sumEl,
  mismatchEl,
  addBtn,
  amountInputEl,
  currency,
}) {
  let categories = [];
  let lineItems = [];

  addBtn.addEventListener('click', () => {
    lineItems.push(emptyLine());
    render();
  });

  amountInputEl.addEventListener('input', updateTotals);

  // Capture edits from line rows without rebinding after each re-render.
  listEl.addEventListener('input', (event) => {
    const row = event.target.closest('[data-line-key]');
    if (!row) {
      return;
    }
    const item = lineItems.find((line) => line.key === Number(row.dataset.lineKey));
    if (!item) {
      return;
    }
    const field = event.target.name;
    if (field === 'description') {
      item.description = event.target.value;
    } else if (field === 'quantity') {
      item.quantity = event.target.value;
      updateRowMathWarning(row, item);
    } else if (field === 'unitPrice') {
      item.unitPrice = event.target.value;
      updateRowMathWarning(row, item);
    } else if (field === 'amount') {
      item.amount = event.target.value;
      updateRowMathWarning(row, item);
      updateTotals();
    } else if (field === 'categoryId') {
      item.categoryId = event.target.value ? Number(event.target.value) : null;
    }
  });

  listEl.addEventListener('click', (event) => {
    const removeBtn = event.target.closest('[data-remove-line]');
    if (!removeBtn) {
      return;
    }
    const key = Number(removeBtn.dataset.removeLine);
    lineItems = lineItems.filter((item) => item.key !== key);
    render();
  });

  function setCategories(nextCategories) {
    categories = nextCategories;
  }

  /** Replace in-memory lines from the server proposal (discards unsaved edits). */
  function loadFromExtraction(proposals) {
    lineItems = (proposals ?? []).map((line) => ({
      key: nextLineKey++,
      description: line.description ?? '',
      quantity: optionalNumberToInput(line.quantity),
      unitPrice: optionalNumberToInput(line.unitPrice),
      amount: line.amount != null ? String(line.amount) : '',
      categoryId: line.categoryId ?? null,
    }));
    render();
  }

  function render() {
    if (lineItems.length === 0) {
      listEl.innerHTML =
        '<p class="review-items-empty">No line items yet. Add a product row if you want a breakdown.</p>';
      updateTotals();
      return;
    }

    const categoryOptionsHtml = buildLineCategoryOptionsHtml(categories);
    listEl.innerHTML = lineItems
      .map((item, index) => {
        const position = index + 1;
        const mathMismatch = lineMathMismatch(item);
        return `
        <div class="review-item-row" data-line-key="${item.key}">
          <div class="review-item-position">
            <span class="review-item-label">№</span>
            <span class="review-item-position-value" aria-label="Line ${position}">${position}</span>
          </div>
          <label class="review-item-field">
            <span class="review-item-label">Description</span>
            <input type="text" name="description" maxlength="255" autocomplete="off"
              value="${escapeHtml(item.description)}" />
          </label>
          <label class="review-item-field">
            <span class="review-item-label">Qty</span>
            <input type="number" name="quantity" step="any" min="0"
              value="${escapeHtml(item.quantity)}" />
          </label>
          <label class="review-item-field">
            <span class="review-item-label">Unit price</span>
            <input type="number" name="unitPrice" step="0.01" min="0"
              value="${escapeHtml(item.unitPrice)}" />
          </label>
          <label class="review-item-field">
            <span class="review-item-label">Amount</span>
            <input type="number" name="amount" step="0.01" min="0"
              value="${escapeHtml(item.amount)}" />
          </label>
          <label class="review-item-field">
            <span class="review-item-label">Category</span>
            <select name="categoryId">
              <option value="">— Select —</option>
              ${categoryOptionsHtml}
            </select>
          </label>
          <button type="button" class="review-item-remove" data-remove-line="${item.key}"
            aria-label="Remove item">Remove</button>
          <p class="review-item-math-mismatch"${mathMismatch ? '' : ' hidden'}>
            Qty × unit price does not match amount.
          </p>
        </div>
      `;
      })
      .join('');

    // Restore selected category after rebuild (options HTML has no selected attrs).
    for (const item of lineItems) {
      const row = listEl.querySelector(`[data-line-key="${item.key}"]`);
      const select = row?.querySelector('select[name="categoryId"]');
      if (select && item.categoryId != null) {
        select.value = String(item.categoryId);
      }
    }

    updateTotals();
  }

  function updateTotals() {
    const linesSum = lineItems.reduce((sum, item) => {
      const value = Number(item.amount);
      return sum + (Number.isFinite(value) ? value : 0);
    }, 0);
    const receiptTotal = Number(amountInputEl.value);
    const receiptLabel = Number.isFinite(receiptTotal) && amountInputEl.value !== ''
      ? formatMoney(receiptTotal)
      : '—';

    sumEl.textContent =
      `Lines: ${formatMoney(linesSum)} ${currency} · Receipt: ${receiptLabel} ${currency}`;

    // Empty list is fine (whole total stays on the receipt). Warn only when
    // there are rows and their sum does not match the receipt amount.
    const hasLines = lineItems.length > 0;
    const receiptOk = Number.isFinite(receiptTotal) && amountInputEl.value !== '';
    const mismatch = hasLines && receiptOk && !amountsEqual(linesSum, receiptTotal);
    if (mismatch) {
      mismatchEl.textContent =
        'Line items and receipt total differ. That can be a discount, deposit, or tip — Approve is still allowed.';
      mismatchEl.hidden = false;
    } else {
      mismatchEl.hidden = true;
      mismatchEl.textContent = '';
    }
  }

  /**
   * Payload for POST approve. error is a short message when a row is not
   * saveable; lineItems is [] when the list is empty (that is allowed).
   * Qty × unit price mismatch is only a yellow note and is not an error here.
   */
  function linesForApprove() {
    const payload = [];
    for (let index = 0; index < lineItems.length; index += 1) {
      const item = lineItems[index];
      const label = `Item ${index + 1}`;
      const description = item.description.trim();
      if (!description) {
        return { error: `${label} needs a description.`, lineItems: [] };
      }
      if (description.length > 255) {
        return { error: `${label} description is too long.`, lineItems: [] };
      }
      const amount = parseOptionalNumber(item.amount);
      if (amount == null || !(amount > 0)) {
        return { error: `${label} needs an amount greater than zero.`, lineItems: [] };
      }
      const quantity = parseOptionalNumber(item.quantity);
      if (item.quantity !== '' && (quantity == null || !(quantity > 0))) {
        return { error: `${label} quantity must be greater than zero.`, lineItems: [] };
      }
      const unitPrice = parseOptionalNumber(item.unitPrice);
      if (item.unitPrice !== '' && (unitPrice == null || !(unitPrice > 0))) {
        return { error: `${label} unit price must be greater than zero.`, lineItems: [] };
      }
      payload.push({
        description,
        quantity,
        unitPrice,
        amount,
        categoryId: item.categoryId ?? null,
      });
    }
    return { error: null, lineItems: payload };
  }

  return { setCategories, loadFromExtraction, linesForApprove };
}

/** Blank or non-numeric → null. Caller decides whether null is an error. */
function parseOptionalNumber(raw) {
  if (raw === '' || raw == null) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

/** Blank in-memory row for "Add item". */
function emptyLine() {
  return {
    key: nextLineKey++,
    description: '',
    quantity: '',
    unitPrice: '',
    amount: '',
    categoryId: null,
  };
}

/** Server null → empty input; otherwise string for the number field. */
function optionalNumberToInput(value) {
  return value != null ? String(value) : '';
}

/**
 * Yellow note only when qty and unit price are both filled and their product
 * does not match the line amount (e.g. waffle 1×1=1 → no note).
 */
function lineMathMismatch(item) {
  if (item.quantity === '' || item.unitPrice === '' || item.amount === '') {
    return false;
  }
  const quantity = Number(item.quantity);
  const unitPrice = Number(item.unitPrice);
  const amount = Number(item.amount);
  if (!Number.isFinite(quantity) || !Number.isFinite(unitPrice) || !Number.isFinite(amount)) {
    return false;
  }
  return !amountsEqual(quantity * unitPrice, amount);
}

/** Toggle the row math note without a full re-render (keeps focus). */
function updateRowMathWarning(row, item) {
  const note = row.querySelector('.review-item-math-mismatch');
  if (!note) {
    return;
  }
  note.hidden = !lineMathMismatch(item);
}

/**
 * Groups with leaves become optgroups; childless groups are plain options
 * so a line can still pick e.g. Shopping when it has no leaves.
 */
function buildLineCategoryOptionsHtml(categories) {
  const groups = categories.filter((category) => category.parentId == null);
  const leaves = categories.filter((category) => category.parentId != null);
  const parts = [];

  for (const group of groups) {
    const children = leaves.filter((leaf) => leaf.parentId === group.id);
    if (children.length === 0) {
      parts.push(
        `<option value="${group.id}">${escapeHtml(group.name)}</option>`,
      );
      continue;
    }
    const options = children
      .map((leaf) => `<option value="${leaf.id}">${escapeHtml(leaf.name)}</option>`)
      .join('');
    parts.push(
      `<optgroup label="${escapeHtml(group.name)}">${options}</optgroup>`,
    );
  }

  return parts.join('');
}

function formatMoney(value) {
  return value.toFixed(2);
}

/** Compare money with cents so 12.1 and 12.10 do not look different. */
function amountsEqual(a, b) {
  return Math.round(a * 100) === Math.round(b * 100);
}

/** Escape text before inserting into HTML attribute or element content. */
function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

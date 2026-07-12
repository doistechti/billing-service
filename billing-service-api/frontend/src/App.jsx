import { useEffect, useMemo, useState } from "react";
import { appConfig } from "./config";

const defaultSourceSystem = "MATER_ECCLESIAE";

function createUniqueSuffix() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function buildCustomerExternalReference() {
  return `ALUNO-${createUniqueSuffix()}`;
}

function buildInvoiceExternalReference() {
  return `MENSALIDADE-${new Date().toISOString().slice(0, 10)}-${createUniqueSuffix()}`;
}

const createCustomerTemplate = () => ({
  sourceSystem: defaultSourceSystem,
  externalReference: buildCustomerExternalReference(),
  name: "",
  email: "",
  document: "",
  phone: ""
});

const createInvoiceTemplate = (customerId = "", sourceSystem = defaultSourceSystem) => ({
  customerId,
  sourceSystem,
  externalReference: buildInvoiceExternalReference(),
  description: "Mensalidade",
  dueDate: new Date().toISOString().slice(0, 10),
  items: [
    {
      description: "Mensalidade",
      quantity: "1",
      unitAmount: "150.00"
    }
  ]
});

const createRequestState = () => ({
  loading: false,
  error: "",
  response: null
});

async function request(path, { method = "GET", apiKey, body } = {}) {
  const url = `${appConfig.apiBaseUrl}${path}`;
  const headers = {
    Accept: "application/json"
  };

  if (apiKey) {
    headers["X-API-Key"] = apiKey;
  }

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  const raw = await response.text();
  const data = raw ? JSON.parse(raw) : null;

  if (!response.ok) {
    const message = data?.message || data?.error || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return data;
}

function prettyJson(value) {
  return JSON.stringify(value, null, 2);
}

function App() {
  const [apiKey, setApiKey] = useState("0ebb5a27ce2dcbd886d49fec60f8bc99");
  const [customerForm, setCustomerForm] = useState(createCustomerTemplate);
  const [invoiceForm, setInvoiceForm] = useState(createInvoiceTemplate);
  const [lookupInvoiceId, setLookupInvoiceId] = useState("");
  const [autoRefresh, setAutoRefresh] = useState(true);

  const [customerRequest, setCustomerRequest] = useState(createRequestState);
  const [invoiceRequest, setInvoiceRequest] = useState(createRequestState);
  const [chargeRequest, setChargeRequest] = useState(createRequestState);
  const [invoiceDetails, setInvoiceDetails] = useState(createRequestState);
  const [chargeDetails, setChargeDetails] = useState(createRequestState);
  const [paymentDetails, setPaymentDetails] = useState(createRequestState);

  const activeInvoiceId = useMemo(() => {
    return lookupInvoiceId || invoiceRequest.response?.id || chargeRequest.response?.invoiceId || "";
  }, [lookupInvoiceId, invoiceRequest.response, chargeRequest.response]);

  useEffect(() => {
    if (!activeInvoiceId || !autoRefresh) {
      return undefined;
    }

    const intervalId = window.setInterval(() => {
      refreshInvoiceBundle(activeInvoiceId, { silent: true });
    }, 5000);

    return () => window.clearInterval(intervalId);
  }, [activeInvoiceId, autoRefresh]);

  async function wrapRequest(setter, action) {
    setter({ loading: true, error: "", response: null });
    try {
      const response = await action();
      setter({ loading: false, error: "", response });
      return response;
    } catch (error) {
      setter({ loading: false, error: error.message, response: null });
      throw error;
    }
  }

  async function createCustomer(event) {
    event.preventDefault();
    try {
      const response = await wrapRequest(setCustomerRequest, () =>
        request("/api/customers", {
          method: "POST",
          apiKey,
          body: customerForm
        })
      );
      setInvoiceForm(createInvoiceTemplate(response.id, customerForm.sourceSystem));
      setCustomerForm((current) => ({
        ...createCustomerTemplate(),
        sourceSystem: current.sourceSystem
      }));
      setLookupInvoiceId("");
      setInvoiceRequest(createRequestState());
      setChargeRequest(createRequestState());
      setInvoiceDetails(createRequestState());
      setChargeDetails(createRequestState());
      setPaymentDetails(createRequestState());
    } catch {
      return;
    }
  }

  async function createInvoice(event) {
    event.preventDefault();
    try {
      const response = await wrapRequest(setInvoiceRequest, () =>
        request("/api/invoices", {
          method: "POST",
          apiKey,
          body: {
            ...invoiceForm,
            items: invoiceForm.items.map((item) => ({
              ...item,
              quantity: Number(item.quantity),
              unitAmount: Number(item.unitAmount)
            }))
          }
        })
      );
      setLookupInvoiceId(response.id);
      setInvoiceForm((current) => createInvoiceTemplate(current.customerId, current.sourceSystem));
      await refreshInvoiceBundle(response.id, { silent: true });
    } catch {
      return;
    }
  }

  async function createCharge() {
    if (!activeInvoiceId) {
      setChargeRequest({ loading: false, error: "Informe ou crie uma invoice antes.", response: null });
      return;
    }

    try {
      const response = await wrapRequest(setChargeRequest, () =>
        request("/api/charges", {
          method: "POST",
          apiKey,
          body: {
            invoiceId: activeInvoiceId,
            gateway: "MERCADO_PAGO"
          }
        })
      );
      await refreshInvoiceBundle(response.invoiceId, { silent: true });
    } catch {
      return;
    }
  }

  async function refreshInvoiceBundle(invoiceId = activeInvoiceId, options = {}) {
    if (!invoiceId) {
      return;
    }

    const setterMode = options.silent
      ? (setter) => setter((current) => ({ ...current, loading: true, error: "" }))
      : (setter) => setter({ loading: true, error: "", response: null });

    setterMode(setInvoiceDetails);
    setterMode(setChargeDetails);
    setterMode(setPaymentDetails);

    const buildState = (response) => ({ loading: false, error: "", response });
    const buildError = (error) => ({ loading: false, error: error.message, response: null });

    const requests = await Promise.allSettled([
      request(`/api/invoices/${invoiceId}`, { apiKey }),
      request(`/api/charges/invoice/${invoiceId}`, { apiKey }),
      request(`/api/payments/invoice/${invoiceId}`, { apiKey })
    ]);

    const [invoiceResult, chargeResult, paymentResult] = requests;
    setInvoiceDetails(invoiceResult.status === "fulfilled" ? buildState(invoiceResult.value) : buildError(invoiceResult.reason));
    setChargeDetails(chargeResult.status === "fulfilled" ? buildState(chargeResult.value) : buildError(chargeResult.reason));
    setPaymentDetails(paymentResult.status === "fulfilled" ? buildState(paymentResult.value) : buildError(paymentResult.reason));
  }

  function updateCustomerField(field, value) {
    setCustomerForm((current) => ({ ...current, [field]: value }));
  }

  function updateInvoiceField(field, value) {
    setInvoiceForm((current) => ({ ...current, [field]: value }));
  }

  function updateInvoiceItem(index, field, value) {
    setInvoiceForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [field]: value } : item
      )
    }));
  }

  function addItem() {
    setInvoiceForm((current) => ({
      ...current,
      items: [...current.items, { description: "", quantity: "1", unitAmount: "0.00" }]
    }));
  }

  function removeItem(index) {
    setInvoiceForm((current) => ({
      ...current,
      items: current.items.filter((_, itemIndex) => itemIndex !== index)
    }));
  }

  function startNewSession() {
    setCustomerForm(createCustomerTemplate());
    setInvoiceForm(createInvoiceTemplate());
    setLookupInvoiceId("");
    setCustomerRequest(createRequestState());
    setInvoiceRequest(createRequestState());
    setChargeRequest(createRequestState());
    setInvoiceDetails(createRequestState());
    setChargeDetails(createRequestState());
    setPaymentDetails(createRequestState());
  }

  function generateCustomerReference() {
    setCustomerForm((current) => ({
      ...current,
      externalReference: buildCustomerExternalReference()
    }));
  }

  function generateInvoiceReference() {
    setInvoiceForm((current) => ({
      ...current,
      externalReference: buildInvoiceExternalReference()
    }));
  }

  const lastCharge = chargeDetails.response?.[0];

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Billing Service</p>
          <h1>Painel de teste da integracao</h1>
        </div>
        <div className="topbar-actions">
          <button type="button" className="secondary" onClick={startNewSession}>
            Nova sessao
          </button>
          <label className="field compact">
            <span>API key</span>
            <input value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
          </label>
          <label className="toggle">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(event) => setAutoRefresh(event.target.checked)}
            />
            <span>Atualizacao automatica</span>
          </label>
        </div>
      </header>

      <main className="layout">
        <section className="panel">
          <div className="panel-header">
            <h2>1. Customer</h2>
          </div>
          <form className="form-grid" onSubmit={createCustomer}>
            <label className="field">
              <span>sourceSystem</span>
              <input value={customerForm.sourceSystem} onChange={(e) => updateCustomerField("sourceSystem", e.target.value)} />
            </label>
            <label className="field">
              <span>externalReference</span>
              <input value={customerForm.externalReference} onChange={(e) => updateCustomerField("externalReference", e.target.value)} />
            </label>
            <div className="inline-action">
              <button type="button" className="secondary" onClick={generateCustomerReference}>
                Gerar referencia
              </button>
            </div>
            <label className="field">
              <span>Nome</span>
              <input value={customerForm.name} onChange={(e) => updateCustomerField("name", e.target.value)} required />
            </label>
            <label className="field">
              <span>Email</span>
              <input type="email" value={customerForm.email} onChange={(e) => updateCustomerField("email", e.target.value)} required />
            </label>
            <label className="field">
              <span>Documento</span>
              <input value={customerForm.document} onChange={(e) => updateCustomerField("document", e.target.value)} />
            </label>
            <label className="field">
              <span>Telefone</span>
              <input value={customerForm.phone} onChange={(e) => updateCustomerField("phone", e.target.value)} />
            </label>
            <div className="form-actions">
              <button type="submit" disabled={customerRequest.loading}>
                {customerRequest.loading ? "Criando..." : "Criar customer"}
              </button>
            </div>
          </form>
          <ResponseBlock title="Resposta" state={customerRequest} />
        </section>

        <section className="panel">
          <div className="panel-header">
            <h2>2. Invoice</h2>
          </div>
          <form className="form-grid" onSubmit={createInvoice}>
            <label className="field">
              <span>customerId</span>
              <input value={invoiceForm.customerId} onChange={(e) => updateInvoiceField("customerId", e.target.value)} required />
            </label>
            <label className="field">
              <span>sourceSystem</span>
              <input value={invoiceForm.sourceSystem} onChange={(e) => updateInvoiceField("sourceSystem", e.target.value)} />
            </label>
            <label className="field">
              <span>externalReference</span>
              <input value={invoiceForm.externalReference} onChange={(e) => updateInvoiceField("externalReference", e.target.value)} />
            </label>
            <div className="inline-action">
              <button type="button" className="secondary" onClick={generateInvoiceReference}>
                Gerar referencia
              </button>
            </div>
            <label className="field">
              <span>Descricao</span>
              <input value={invoiceForm.description} onChange={(e) => updateInvoiceField("description", e.target.value)} required />
            </label>
            <label className="field">
              <span>Vencimento</span>
              <input type="date" value={invoiceForm.dueDate} onChange={(e) => updateInvoiceField("dueDate", e.target.value)} />
            </label>
            <div className="items-block">
              <div className="items-header">
                <h3>Itens</h3>
                <button type="button" className="secondary" onClick={addItem}>
                  Adicionar item
                </button>
              </div>
              {invoiceForm.items.map((item, index) => (
                <div className="item-row" key={index}>
                  <label className="field grow">
                    <span>Descricao</span>
                    <input
                      value={item.description}
                      onChange={(e) => updateInvoiceItem(index, "description", e.target.value)}
                      required
                    />
                  </label>
                  <label className="field small">
                    <span>Qtd</span>
                    <input
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={item.quantity}
                      onChange={(e) => updateInvoiceItem(index, "quantity", e.target.value)}
                      required
                    />
                  </label>
                  <label className="field small">
                    <span>Valor</span>
                    <input
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={item.unitAmount}
                      onChange={(e) => updateInvoiceItem(index, "unitAmount", e.target.value)}
                      required
                    />
                  </label>
                  <button
                    type="button"
                    className="icon-button"
                    onClick={() => removeItem(index)}
                    disabled={invoiceForm.items.length === 1}
                    aria-label="Remover item"
                  >
                    -
                  </button>
                </div>
              ))}
            </div>
            <div className="form-actions">
              <button type="submit" disabled={invoiceRequest.loading}>
                {invoiceRequest.loading ? "Criando..." : "Criar invoice"}
              </button>
            </div>
          </form>
          <ResponseBlock title="Resposta" state={invoiceRequest} />
        </section>

        <section className="panel">
          <div className="panel-header">
            <h2>3. Charge e checkout</h2>
          </div>
          <div className="charge-actions">
            <label className="field">
              <span>Invoice ativa</span>
              <input value={activeInvoiceId} onChange={(e) => setLookupInvoiceId(e.target.value)} placeholder="UUID da invoice" />
            </label>
            <div className="hint-box">
              Customer criado prepara uma nova invoice automaticamente. Invoice criada gera um novo rascunho para o proximo teste e preserva a invoice ativa para charge e acompanhamento.
            </div>
            <div className="button-row">
              <button type="button" onClick={createCharge} disabled={chargeRequest.loading}>
                {chargeRequest.loading ? "Criando..." : "Criar charge"}
              </button>
              <button type="button" className="secondary" onClick={() => refreshInvoiceBundle()}>
                Atualizar status
              </button>
              {chargeRequest.response?.paymentUrl && (
                <a className="button-link" href={chargeRequest.response.paymentUrl} target="_blank" rel="noreferrer">
                  Abrir checkout
                </a>
              )}
            </div>
          </div>
          <ResponseBlock title="Charge criada" state={chargeRequest} />
          {lastCharge && (
            <div className="status-strip">
              <div>
                <span className="label">Gateway</span>
                <strong>{lastCharge.gateway}</strong>
              </div>
              <div>
                <span className="label">Status</span>
                <strong>{lastCharge.status}</strong>
              </div>
              <div>
                <span className="label">Preference ID</span>
                <strong>{lastCharge.gatewayPreferenceId || "-"}</strong>
              </div>
              <div>
                <span className="label">Payment ID</span>
                <strong>{lastCharge.gatewayPaymentId || "-"}</strong>
              </div>
            </div>
          )}
        </section>

        <section className="panel span-2">
          <div className="panel-header">
            <h2>4. Acompanhamento</h2>
          </div>
          <div className="overview-grid">
            <DataCard title="Invoice" state={invoiceDetails} />
            <DataCard title="Charges" state={chargeDetails} />
            <DataCard title="Payments" state={paymentDetails} />
          </div>
        </section>
      </main>
    </div>
  );
}

function ResponseBlock({ title, state }) {
  return (
    <div className="response-block">
      <div className="response-title">{title}</div>
      {state.error ? <div className="error-box">{state.error}</div> : null}
      <pre>{state.response ? prettyJson(state.response) : "Sem dados"}</pre>
    </div>
  );
}

function DataCard({ title, state }) {
  return (
    <div className="data-card">
      <div className="data-card-header">
        <h3>{title}</h3>
        {state.loading ? <span className="badge">carregando</span> : null}
      </div>
      {state.error ? <div className="error-box">{state.error}</div> : null}
      <pre>{state.response ? prettyJson(state.response) : "Sem dados"}</pre>
    </div>
  );
}

export default App;

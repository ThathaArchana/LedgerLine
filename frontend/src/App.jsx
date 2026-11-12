import { useCallback, useEffect, useMemo, useState } from "react";

const navigation = [
  { id: "overview", label: "Overview", icon: "◈" },
  { id: "accounts", label: "Accounts", icon: "◌" },
  { id: "payments", label: "Payments", icon: "↕" },
];

function getStoredUser() {
  const storedUser = sessionStorage.getItem("ledgerline-user");
  if (!storedUser) return null;
  try {
    return JSON.parse(storedUser);
  } catch {
    sessionStorage.removeItem("ledgerline-user");
    return null;
  }
}

function formatMoney(amount, currency) {
  const value = Number(amount);
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

function formatDate(value) {
  return new Date(value).toLocaleString("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function App() {
  const [resetToken, setResetToken] = useState(() => {
    const tokenFromLink = new URLSearchParams(window.location.hash.slice(1)).get("resetToken") || "";
    if (tokenFromLink) {
      sessionStorage.removeItem("ledgerline-token");
      sessionStorage.removeItem("ledgerline-user");
    }
    return tokenFromLink;
  });
  const [token, setToken] = useState(() => sessionStorage.getItem("ledgerline-token") || "");
  const [currentUser, setCurrentUser] = useState(getStoredUser);
  const [authMode, setAuthMode] = useState(() => (resetToken ? "reset" : "login"));
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [authMessage, setAuthMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedTab, setSelectedTab] = useState("overview");
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [dashboardMessage, setDashboardMessage] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isWorking, setIsWorking] = useState(false);
  const [movementType, setMovementType] = useState("deposit");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [adminUsers, setAdminUsers] = useState([]);
  const [adminAccounts, setAdminAccounts] = useState([]);
  const [adminTransactions, setAdminTransactions] = useState([]);
  const [adminMessage, setAdminMessage] = useState("");
  const [isAdminLoading, setIsAdminLoading] = useState(false);

  const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
  const visibleNavigation = currentUser?.role === "ADMIN"
    ? [...navigation, { id: "admin", label: "Admin dashboard", icon: "✓" }]
    : navigation;

  const apiFetch = useCallback(async (path, { method = "GET", body, headers = {} } = {}) => {
    const requestHeaders = { ...headers };
    if (body !== undefined) requestHeaders["Content-Type"] = "application/json";
    if (token) requestHeaders.Authorization = `Bearer ${token}`;

    const response = await fetch(`${apiUrl}${path}`, {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const responseText = await response.text();
    let result = null;
    if (responseText) {
      try {
        result = JSON.parse(responseText);
      } catch {
        result = { message: responseText };
      }
    }

    if (!response.ok) {
      throw new Error(result?.message || `Request failed (${response.status}).`);
    }
    return result;
  }, [apiUrl, token]);

  const refreshData = useCallback(async () => {
    setIsLoading(true);
    setDashboardMessage("");
    try {
      const loadedAccounts = await apiFetch("/accounts");
      const histories = await Promise.all(
        loadedAccounts.map((account) => apiFetch(`/transactions/${account.id}`)),
      );
      const loadedTransactions = histories.flat().sort(
        (left, right) => new Date(right.createdAt) - new Date(left.createdAt),
      );
      setAccounts(loadedAccounts);
      setTransactions(loadedTransactions);
      setSelectedAccountId((current) => (
        loadedAccounts.some((account) => String(account.id) === current)
          ? current
          : String(loadedAccounts[0]?.id || "")
      ));
    } catch (error) {
      setDashboardMessage(error.message || "Could not load your account data.");
    } finally {
      setIsLoading(false);
    }
  }, [apiFetch]);

  const refreshAdminData = useCallback(async () => {
    setIsAdminLoading(true);
    setAdminMessage("");
    try {
      const [users, loadedAccounts, loadedTransactions] = await Promise.all([
        apiFetch("/admin/users"),
        apiFetch("/admin/accounts"),
        apiFetch("/admin/transactions"),
      ]);
      setAdminUsers(users);
      setAdminAccounts(loadedAccounts);
      setAdminTransactions(loadedTransactions);
    } catch (error) {
      setAdminMessage(error.message || "Could not load admin dashboard data.");
    } finally {
      setIsAdminLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    if (token) void refreshData();
  }, [token, refreshData]);

  useEffect(() => {
    if (token && currentUser?.role === "ADMIN" && selectedTab === "admin") {
      void refreshAdminData();
    }
  }, [token, currentUser, selectedTab, refreshAdminData]);

  const selectedAccount = accounts.find((account) => String(account.id) === selectedAccountId);
  const accountById = useMemo(
    () => new Map(accounts.map((account) => [account.id, account])),
    [accounts],
  );
  const recentTransactions = transactions.slice(0, 5);

  const submitAuth = async (event) => {
    event.preventDefault();
    setAuthMessage("");
    setIsSubmitting(true);

    try {
      const isSignup = authMode === "signup";
      const isForgotPassword = authMode === "forgot";
      const isPasswordReset = authMode === "reset";
      if (isPasswordReset && password !== confirmPassword) {
        setAuthMessage("Passwords do not match.");
        return;
      }

      const endpoint = isSignup
        ? "/auth/signup"
        : isForgotPassword
          ? "/auth/forgot-password"
          : isPasswordReset
            ? "/auth/reset-password"
            : "/auth/login";
      const payload = isSignup
        ? { name: fullName, email, password }
        : isForgotPassword
          ? { email }
          : isPasswordReset
            ? { token: resetToken, newPassword: password }
            : { email, password };
      const response = await fetch(`${apiUrl}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const responseText = await response.text();
      let result = null;
      if (responseText) {
        try {
          result = JSON.parse(responseText);
        } catch {
          result = { message: responseText };
        }
      }

      if (!response.ok) {
        if (!isSignup && !isForgotPassword && !isPasswordReset && response.status === 401) {
          setAuthMessage("Email or password is incorrect. If you are new to LedgerLine, choose Sign Up first.");
          return;
        }
        if (!isSignup && !isForgotPassword && !isPasswordReset && response.status === 403) {
          setAuthMessage("Your account is awaiting admin approval.");
          return;
        }
        if (isPasswordReset && response.status === 400) {
          setAuthMessage("This reset link is invalid or expired. Request another one.");
          return;
        }
        if (isForgotPassword && response.status === 503) {
          setAuthMessage("Password reset email is not configured yet. Contact the administrator.");
          return;
        }
        if (isSignup && response.status === 409) {
          setAuthMode("login");
          setPassword("");
          setAuthMessage("This email is already registered. Sign in with your existing password.");
          return;
        }
        throw new Error(result?.message || `Request failed (${response.status}).`);
      }

      if (isSignup) {
        setAuthMode("login");
        setPassword("");
        setAuthMessage(result.message || "Signup request submitted. An admin must approve your account before you can sign in.");
      } else if (isForgotPassword) {
        setAuthMessage(result?.message || "If an account exists for that email, password reset instructions will be sent.");
      } else if (isPasswordReset) {
        setResetToken("");
        window.history.replaceState({}, "", window.location.pathname);
        setAuthMode("login");
        setPassword("");
        setConfirmPassword("");
        setAuthMessage("Password reset successfully. Sign in with your new password.");
      } else {
        sessionStorage.setItem("ledgerline-token", result.token);
        sessionStorage.setItem("ledgerline-user", JSON.stringify(result));
        setCurrentUser(result);
        setToken(result.token);
        setSelectedTab(result.role === "ADMIN" ? "admin" : "overview");
      }
    } catch (error) {
      setAuthMessage(error instanceof TypeError
        ? "Cannot connect to LedgerLine. Start the backend server and try again."
        : error.message || "Could not reach LedgerLine. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const createAccount = async () => {
    setIsWorking(true);
    setActionMessage("");
    try {
      const account = await apiFetch("/accounts", { method: "POST", body: { currency: "INR" } });
      await refreshData();
      setSelectedAccountId(String(account.id));
      setActionMessage("INR account created.");
    } catch (error) {
      setActionMessage(error.message || "Could not create the account.");
    } finally {
      setIsWorking(false);
    }
  };

  const submitMovement = async (event) => {
    event.preventDefault();
    setActionMessage("");
    if (!selectedAccount) {
      setActionMessage("Create or select an account first.");
      return;
    }

    setIsWorking(true);
    try {
      await apiFetch(`/transactions/${movementType}`, {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: {
          accountId: selectedAccount.id,
          amount: Number(amount),
          description: description.trim(),
        },
      });
      setAmount("");
      setDescription("");
      setActionMessage(`${movementType === "deposit" ? "Deposit" : "Withdrawal"} recorded.`);
      await refreshData();
    } catch (error) {
      setActionMessage(error.message || "Could not record this transaction.");
    } finally {
      setIsWorking(false);
    }
  };

  const logout = () => {
    sessionStorage.removeItem("ledgerline-token");
    sessionStorage.removeItem("ledgerline-user");
    setToken("");
    setCurrentUser(null);
    setAccounts([]);
    setTransactions([]);
    setSelectedAccountId("");
    setSelectedTab("overview");
  };

  const approveUser = async (userId) => {
    setAdminMessage("");
    try {
      await apiFetch(`/admin/users/${userId}/approve`, { method: "POST" });
      await refreshAdminData();
    } catch (error) {
      setAdminMessage(error.message || "Could not approve this account.");
    }
  };

  const returnToLogin = () => {
    setAuthMode("login");
    setAuthMessage("");
    setPassword("");
    setConfirmPassword("");
    if (resetToken) {
      setResetToken("");
      window.history.replaceState({}, "", window.location.pathname);
    }
  };

  if (!token) {
    const isSignup = authMode === "signup";
    const isForgotPassword = authMode === "forgot";
    const isPasswordReset = authMode === "reset";
    const isAuthSuccess = authMessage.startsWith("Signup request submitted")
      || authMessage.startsWith("If an account exists")
      || authMessage.startsWith("Password reset successfully");

    return (
      <div className="login-shell">
        <div className="login-panel">
          <div className="brand-header">
            <div className="brand-mark">◌</div>
            <div>
              <div className="brand-tag">Secure Access</div>
              <h1>LedgerLine</h1>
            </div>
          </div>
          {!isPasswordReset && (
            <div className="auth-switch" role="tablist" aria-label="Account access">
              <button
                type="button"
                role="tab"
                aria-selected={authMode === "login"}
                className={authMode === "login" ? "selected" : ""}
                onClick={returnToLogin}
              >
                Login
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={isSignup}
                className={isSignup ? "selected" : ""}
                onClick={() => { setAuthMode("signup"); setAuthMessage(""); }}
              >
                Sign Up
              </button>
            </div>
          )}

          <div className="auth-heading">
            <h2>{isSignup
              ? "Create your account"
              : isForgotPassword
                ? "Forgot your password?"
                : isPasswordReset
                  ? "Set a new password"
                  : "Welcome back"}</h2>
            <p>{isSignup
              ? "Submit your details. An admin must approve your account before you can sign in."
              : isForgotPassword
                ? "Enter your email and we will send a password reset link if an account exists."
                : isPasswordReset
                  ? "Choose a new password for your LedgerLine account."
                  : "Sign in to manage your accounts and transactions."}</p>
          </div>

          <form className="login-form" onSubmit={submitAuth}>
            {isSignup && (
              <div className="input-group">
                <label htmlFor="full-name">Full name</label>
                <input
                  id="full-name"
                  type="text"
                  autoComplete="name"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  placeholder="Your name"
                  maxLength={100}
                  required
                />
              </div>
            )}
            {!isPasswordReset && (
              <div className="input-group">
                <label htmlFor="email">Email</label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="name@example.com"
                  required
                />
              </div>
            )}
            {!isForgotPassword && !isPasswordReset && (
              <div className="input-group">
                <label htmlFor="password">Password</label>
                <input
                  id="password"
                  type="password"
                  autoComplete={isSignup ? "new-password" : "current-password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder={isSignup ? "At least 8 characters" : "Enter your password"}
                  minLength={8}
                  maxLength={72}
                  required
                />
              </div>
            )}
            {isPasswordReset && (
              <>
                <div className="input-group">
                  <label htmlFor="new-password">New password</label>
                  <input
                    id="new-password"
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="At least 8 characters"
                    minLength={8}
                    maxLength={72}
                    required
                  />
                </div>
                <div className="input-group">
                  <label htmlFor="confirm-password">Confirm new password</label>
                  <input
                    id="confirm-password"
                    type="password"
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    placeholder="Enter the new password again"
                    minLength={8}
                    maxLength={72}
                    required
                  />
                </div>
              </>
            )}
            {authMode === "login" && (
              <button
                type="button"
                className="text-button"
                onClick={() => { setAuthMode("forgot"); setAuthMessage(""); }}
              >
                Forgot password?
              </button>
            )}
            {authMessage && (
              <div
                className={`auth-message ${isAuthSuccess ? "success" : "error"}`}
                role="status"
              >
                {authMessage}
              </div>
            )}
            <button type="submit" className="login-btn" disabled={isSubmitting}>
              {isSubmitting
                ? "Please wait..."
                : isSignup
                  ? "Create Account"
                  : isForgotPassword
                    ? "Send reset link"
                    : isPasswordReset
                      ? "Set new password"
                      : "Sign In"}
            </button>
            {(isForgotPassword || isPasswordReset) && (
              <button type="button" className="text-button" onClick={returnToLogin}>
                Back to sign in
              </button>
            )}
          </form>
        </div>
      </div>
    );
  }

  const renderTransactions = (items) => {
    if (!items.length) return <p className="empty-state">No transactions yet.</p>;
    return (
      <div className="table-wrap">
        <table className="transactions-table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Account</th>
              <th>Description</th>
              <th>Date</th>
              <th>Amount</th>
              <th>Balance after</th>
            </tr>
          </thead>
          <tbody>
            {items.map((transaction) => {
              const account = accountById.get(transaction.accountId);
              const isDeposit = transaction.transactionType === "DEPOSIT";
              return (
                <tr key={transaction.id}>
                  <td>{isDeposit ? "Deposit" : "Withdrawal"}</td>
                  <td>
                    <span>
                      {account?.currency || transaction.currency || "INR"} ••••{" "}
                      {(account?.accountNumber || transaction.accountNumber || "").slice(-4) || "—"}
                    </span>
                    <span className="muted transaction-account-holder">
                      {account?.accountHolder || transaction.accountHolder || ""}
                    </span>
                  </td>
                  <td>{transaction.description || "—"}</td>
                  <td>{formatDate(transaction.createdAt)}</td>
                  <td className={isDeposit ? "credit-text" : "debit-text"}>
                    {formatMoney(transaction.amount, account?.currency || "INR")}
                  </td>
                  <td>{formatMoney(transaction.balanceAfter, account?.currency || "INR")}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const renderContent = () => {
    if (selectedTab === "admin" && currentUser?.role === "ADMIN") {
      const pendingUsers = adminUsers.filter((user) => user.status === "PENDING");
      const summarizeByCurrency = (type) => {
        const totals = {};
        adminTransactions
          .filter((transaction) => transaction.transactionType === type)
          .forEach((transaction) => {
            const currency = transaction.currency || "INR";
            totals[currency] = (totals[currency] || 0) + Number(transaction.amount);
          });
        const entries = Object.entries(totals);
        return entries.length
          ? entries.map(([currency, total]) => formatMoney(total, currency)).join(" · ")
          : formatMoney(0, "INR");
      };

      return (
        <section className="dynamic-panel full-panel">
          <div className="panel-header full-header">
            <div>
              <h3>Admin dashboard</h3>
              <p className="muted">Review users, accounts, deposits, and withdrawals.</p>
            </div>
            <button className="small-btn" onClick={refreshAdminData} disabled={isAdminLoading}>
              {isAdminLoading ? "Refreshing..." : "Refresh"}
            </button>
          </div>
          {adminMessage && <p className="error-banner" role="alert">{adminMessage}</p>}
          <div className="stats-row">
            <div className="mini-stat">
              <div className="mini-label">Users</div>
              <div className="mini-value">{adminUsers.length}</div>
              <div className="mini-detail">{pendingUsers.length} pending approval</div>
            </div>
            <div className="mini-stat">
              <div className="mini-label">Accounts</div>
              <div className="mini-value">{adminAccounts.length}</div>
              <div className="mini-detail">Created by LedgerLine users</div>
            </div>
            <div className="mini-stat">
              <div className="mini-label">Total deposits</div>
              <div className="mini-value">{summarizeByCurrency("DEPOSIT")}</div>
              <div className="mini-detail">Grouped by account currency</div>
            </div>
            <div className="mini-stat">
              <div className="mini-label">Total withdrawals</div>
              <div className="mini-value">{summarizeByCurrency("WITHDRAW")}</div>
              <div className="mini-detail">Grouped by account currency</div>
            </div>
          </div>

          <div className="transaction-history">
            <h3>Users and signup approvals</h3>
            {adminUsers.length ? (
              <div className="table-wrap">
                <table className="transactions-table">
                  <thead>
                    <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Registered</th><th>Action</th></tr>
                  </thead>
                  <tbody>
                    {adminUsers.map((user) => (
                      <tr key={user.id}>
                        <td>{user.name}</td>
                        <td>{user.email}</td>
                        <td>{user.role}</td>
                        <td>
                          <span className={`status-pill ${user.status === "ACTIVE" ? "success" : "warning"}`}>
                            {user.status}
                          </span>
                        </td>
                        <td>{formatDate(user.createdAt)}</td>
                        <td>{user.status === "PENDING"
                          ? <button className="small-btn" onClick={() => approveUser(user.id)}>Approve</button>
                          : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="empty-state">{isAdminLoading ? "Loading users..." : "No users found."}</p>
            )}
          </div>

          <div className="transaction-history">
            <h3>Accounts created</h3>
            {adminAccounts.length ? (
              <div className="table-wrap">
                <table className="transactions-table">
                  <thead>
                    <tr><th>Account holder</th><th>Email</th><th>Account</th><th>Created</th><th>Balance</th></tr>
                  </thead>
                  <tbody>
                    {adminAccounts.map((account) => (
                      <tr key={account.id}>
                        <td>{account.accountHolder}</td>
                        <td>{account.ownerEmail || "—"}</td>
                        <td>•••• {account.accountNumber.slice(-4)}</td>
                        <td>{formatDate(account.createdAt)}</td>
                        <td>{formatMoney(account.balance, account.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="empty-state">{isAdminLoading ? "Loading accounts..." : "No accounts created yet."}</p>
            )}
          </div>

          <div className="transaction-history">
            <h3>Deposits and withdrawals</h3>
            {adminTransactions.length ? (
              <div className="table-wrap">
                <table className="transactions-table">
                  <thead>
                    <tr>
                      <th>Date</th><th>User</th><th>Email</th><th>Account</th>
                      <th>Type</th><th>Amount</th><th>Balance after</th><th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {adminTransactions.map((transaction) => {
                      const isDeposit = transaction.transactionType === "DEPOSIT";
                      return (
                        <tr key={transaction.id}>
                          <td>{formatDate(transaction.createdAt)}</td>
                          <td>{transaction.accountHolder}</td>
                          <td>{transaction.ownerEmail || "—"}</td>
                          <td>•••• {transaction.accountNumber.slice(-4)}</td>
                          <td>{isDeposit ? "Deposit" : "Withdrawal"}</td>
                          <td className={isDeposit ? "credit-text" : "debit-text"}>
                            {formatMoney(transaction.amount, transaction.currency || "INR")}
                          </td>
                          <td>{formatMoney(transaction.balanceAfter, transaction.currency || "INR")}</td>
                          <td>{transaction.description || "—"}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="empty-state">
                {isAdminLoading ? "Loading transactions..." : "No deposits or withdrawals yet."}
              </p>
            )}
          </div>
        </section>
      );
    }

    if (selectedTab === "accounts") {
      return (
        <section className="dynamic-panel full-panel">
          <div className="panel-header full-header">
            <div>
              <h3>Your accounts</h3>
              <p className="muted">Balances and account details from LedgerLine.</p>
            </div>
            <button className="small-btn" onClick={createAccount} disabled={isWorking}>
              {isWorking ? "Creating..." : "Create INR account"}
            </button>
          </div>
          {accounts.length ? (
            <div className="data-list">
              {accounts.map((account) => (
                <button
                  type="button"
                  key={account.id}
                  className={`data-card account-card account-select ${selectedAccountId === String(account.id) ? "account-selected" : ""}`}
                  onClick={() => setSelectedAccountId(String(account.id))}
                >
                  <span>
                    <span className="mini-label">Account ending in {account.accountNumber.slice(-4)}</span>
                    <span className="muted account-owner">{account.accountHolder}</span>
                  </span>
                  <span className="account-meta">
                    <span className="status-pill success">Active</span>
                    <strong>{formatMoney(account.balance, account.currency)}</strong>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <p className="empty-state">
              {isLoading ? "Loading accounts..." : "No accounts yet. Create an INR account to get started."}
            </p>
          )}
        </section>
      );
    }

    if (selectedTab === "payments") {
      return (
        <section className="dynamic-panel full-panel">
          <h3>Deposit or withdraw</h3>
          <p className="muted">Record a money movement on one of your LedgerLine accounts.</p>
          {accounts.length > 0 ? (
            <form className="movement-form" onSubmit={submitMovement}>
              <div className="form-grid">
                <div className="input-group">
                  <label htmlFor="movement-type">Transaction type</label>
                  <select
                    id="movement-type"
                    value={movementType}
                    onChange={(event) => setMovementType(event.target.value)}
                  >
                    <option value="deposit">Deposit</option>
                    <option value="withdraw">Withdraw</option>
                  </select>
                </div>
                <div className="input-group">
                  <label htmlFor="movement-account">Account</label>
                  <select
                    id="movement-account"
                    value={selectedAccountId}
                    onChange={(event) => setSelectedAccountId(event.target.value)}
                    required
                  >
                    {accounts.map((account) => (
                      <option key={account.id} value={account.id}>
                        {account.currency} •••• {account.accountNumber.slice(-4)}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="input-group">
                  <label htmlFor="movement-amount">Amount</label>
                  <input
                    id="movement-amount"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                    placeholder="0.00"
                    required
                  />
                </div>
                <div className="input-group">
                  <label htmlFor="movement-description">Description</label>
                  <input
                    id="movement-description"
                    type="text"
                    maxLength={255}
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="Optional note"
                  />
                </div>
              </div>
              <button type="submit" className="login-btn movement-submit" disabled={isWorking}>
                {isWorking ? "Processing..." : `Record ${movementType}`}
              </button>
            </form>
          ) : (
            <p className="empty-state">
              {isLoading ? "Loading accounts..." : "Create an account before recording a transaction."}
            </p>
          )}
          {actionMessage && <p className="status-note" role="status">{actionMessage}</p>}
          <div className="transaction-history">
            <h3>Transaction history</h3>
            {renderTransactions(transactions)}
          </div>
        </section>
      );
    }

    return (
      <section className="content-grid overview-grid">
        <div className="left-column">
          <div className="stats-row">
            <div className="mini-stat">
              <div className="mini-label">Accounts</div>
              <div className="mini-value">{accounts.length}</div>
              <div className="mini-detail">Your active accounts</div>
            </div>
            <div className="mini-stat">
              <div className="mini-label">Transactions</div>
              <div className="mini-value">{transactions.length}</div>
              <div className="mini-detail">Recorded activity</div>
            </div>
            <div className="mini-stat">
              <div className="mini-label">Selected account</div>
              <div className="mini-value">{selectedAccount?.currency || "—"}</div>
              <div className="mini-detail">
                {selectedAccount ? `•••• ${selectedAccount.accountNumber.slice(-4)}` : "Create an account to begin"}
              </div>
            </div>
          </div>
          <div className="panel transactions-panel">
            <div className="panel-header">
              <h3>Recent transactions</h3>
              <button className="small-btn" onClick={() => setSelectedTab("payments")}>View all</button>
            </div>
            {recentTransactions.length
              ? renderTransactions(recentTransactions)
              : <p className="empty-state">{isLoading ? "Loading activity..." : "Your transactions will appear here."}</p>}
          </div>
        </div>
        <div className="right-column">
          <div className="panel balance-panel">
            <div className="small-label">Selected account balance</div>
            <div className="account-number">
              {selectedAccount
                ? formatMoney(selectedAccount.balance, selectedAccount.currency)
                : "No account"}
            </div>
            <div className="account-sub">
              {selectedAccount
                ? `Account ending in ${selectedAccount.accountNumber.slice(-4)}`
                : "Create an account to see its balance."}
            </div>
            <button className="ghost-btn balance-action" onClick={() => setSelectedTab("accounts")}>
              View accounts
            </button>
          </div>
          <div className="panel">
            <div className="panel-header">
              <h3>Get started</h3>
            </div>
            <p className="muted">Create an account, then record deposits or withdrawals. Balances and ledger history update from the backend.</p>
            <button className="small-btn" onClick={() => setSelectedTab("payments")}>Make a transaction</button>
          </div>
        </div>
      </section>
    );
  };

  return (
    <div className="bank-shell">
      <aside className="sidebar">
        <div className="brand-badge">◌</div>
        <nav className="side-menu" aria-label="Main navigation">
          {visibleNavigation.map((item) => (
            <button
              type="button"
              key={item.id}
              className={`side-icon ${selectedTab === item.id ? "active" : ""}`}
              onClick={() => { setSelectedTab(item.id); setActionMessage(""); }}
              title={item.label}
              aria-label={item.label}
            >
              {item.icon}
            </button>
          ))}
        </nav>
      </aside>

      <main className="dashboard-panel">
        <header className="topbar">
          <div className="welcome-block">
            <div className="avatar">{currentUser?.name?.slice(0, 1).toUpperCase() || "L"}</div>
            <div>
              <h2>Welcome{currentUser?.name ? `, ${currentUser.name}` : ""}</h2>
              {currentUser?.email && <div className="muted">{currentUser.email}</div>}
            </div>
          </div>
          <div className="top-actions">
            {currentUser?.role === "ADMIN" ? (
              <button className="ghost-btn" onClick={() => setSelectedTab("admin")}>Admin dashboard</button>
            ) : (
              <button className="ghost-btn" onClick={createAccount} disabled={isWorking}>Create Account</button>
            )}
            <button className="ghost-btn logout-btn" onClick={logout}>Logout</button>
          </div>
        </header>

        <div className="detail-banner">
          {visibleNavigation.find((item) => item.id === selectedTab)?.label}
          <button
            type="button"
            className="refresh-btn"
            onClick={selectedTab === "admin" ? refreshAdminData : refreshData}
            disabled={selectedTab === "admin" ? isAdminLoading : isLoading}
          >
            {selectedTab === "admin"
              ? (isAdminLoading ? "Refreshing..." : "Refresh")
              : (isLoading ? "Refreshing..." : "Refresh")}
          </button>
        </div>

        {dashboardMessage && <p className="error-banner" role="alert">{dashboardMessage}</p>}
        {actionMessage && selectedTab !== "payments" && (
          <p className="status-note" role="status">{actionMessage}</p>
        )}
        {renderContent()}
      </main>
    </div>
  );
}

export default App;

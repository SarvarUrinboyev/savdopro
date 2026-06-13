import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { ToastProvider } from './components/Toast.jsx';
import { AuthProvider } from './context/Auth.jsx';
import { SettingsProvider } from './context/Settings.jsx';
import { ShopProvider } from './context/Shop.jsx';
import './styles/index.css';

if (
  window.location.protocol === 'http:'
  && window.location.hostname.endsWith('.nip.io')
) {
  window.location.replace(`https://${window.location.host}${window.location.pathname}${window.location.search}${window.location.hash}`);
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <SettingsProvider>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <ToastProvider>
          <AuthProvider>
            <ShopProvider>
              <App />
            </ShopProvider>
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </SettingsProvider>
  </React.StrictMode>,
);

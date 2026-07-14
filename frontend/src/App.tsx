import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import BusinessList from './pages/BusinessList';
import BusinessDetail from './pages/BusinessDetail';
import OwnerDashboard from './pages/OwnerDashboard';
import { useAuthStore } from './store/authStore';

function RequireOwner({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'BUSINESS_OWNER') return <Navigate to="/businesses" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/businesses" element={<BusinessList />} />
          <Route path="/businesses/:id" element={<BusinessDetail />} />
          <Route
            path="/dashboard"
            element={
              <RequireOwner>
                <OwnerDashboard />
              </RequireOwner>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

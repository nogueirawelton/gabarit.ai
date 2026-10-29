import { Navigate, Route, Routes } from "react-router";

import { RequireAuth } from "./auth/RequireAuth";
import { AppLayout } from "./components/AppLayout";
import { ExamPage } from "./pages/ExamPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { ProfilePage } from "./pages/ProfilePage";
import { StudiesPage } from "./pages/StudiesPage";
import { StudyTargetPage } from "./pages/StudyTargetPage";

export function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      {/* área logada: uma moldura (cabeçalho + dock) para todas as páginas */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route path="/concursos/:id" element={<ExamPage />} />
        <Route path="/estudos" element={<StudiesPage />} />
        <Route path="/estudos/:id" element={<StudyTargetPage />} />
        <Route path="/perfil" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

import { Navigate, Route, Routes, useParams } from "react-router";

import { RequireAuth } from "./auth/RequireAuth";
import { AppLayout } from "./components/AppLayout";
import { ExamPage } from "./pages/ExamPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { NewStudyTargetPage } from "./pages/NewStudyTargetPage";
import { ProfilePage } from "./pages/ProfilePage";
import { SimulationPage } from "./pages/SimulationPage";
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
        <Route path="/meus-concursos" element={<StudiesPage />} />
        <Route path="/meus-concursos/novo" element={<NewStudyTargetPage />} />
        <Route path="/meus-concursos/:id" element={<StudyTargetPage />} />
        {/* endereços antigos (links salvos, avisos já lidos) */}
        <Route path="/estudos" element={<Navigate to="/meus-concursos" replace />} />
        <Route path="/estudos/:id" element={<OldStudyRedirect />} />
        <Route path="/simulados/:id" element={<SimulationPage />} />
        <Route path="/perfil" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function OldStudyRedirect() {
  return <Navigate to={`/meus-concursos/${useParams().id ?? ""}`} replace />;
}

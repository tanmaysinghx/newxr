import { Routes } from '@angular/router';
import { PulseEscalations, PulseOnCall } from './oncall-pages';
import { PulseAlert, PulseAlerts, PulseIncident, PulseIncidents, PulseLayout, PulseTeams } from './pulse-pages';
import { PulseIntegrations, PulseStatus } from './status-pages';

export const PULSE_ROUTES: Routes = [
  { path: '', component: PulseTeams, title: 'Teams · Pulse' },
  {
    path: ':key',
    component: PulseLayout,
    children: [
      { path: '', redirectTo: 'alerts', pathMatch: 'full' },
      { path: 'alerts', component: PulseAlerts, title: 'Alerts · Pulse' },
      { path: 'alerts/:id', component: PulseAlert, title: 'Alert · Pulse' },
      { path: 'incidents', component: PulseIncidents, title: 'Incidents · Pulse' },
      { path: 'incidents/:incidentKey', component: PulseIncident, title: 'Incident · Pulse' },
      { path: 'oncall', component: PulseOnCall, title: 'On-call · Pulse' },
      { path: 'escalations', component: PulseEscalations, title: 'Escalations · Pulse' },
      { path: 'status', component: PulseStatus, title: 'Status page · Pulse' },
      { path: 'integrations', component: PulseIntegrations, title: 'Integrations · Pulse' },
    ],
  },
];

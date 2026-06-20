import {toast} from '../shared/toasts.js'

// Génération de la fiche d'intervention imprimable (PDF via la fenêtre d'impression du
// navigateur). Logique pure extraite de SpInterventionsPage : prend l'intervention + sa main
// courante + ses CRI, construit le HTML et lance l'impression. Aucune dépendance à l'UI Svelte.

const CRI_LABEL = { BROUILLON: 'Brouillon', SOUMIS: 'Soumis', VALIDE: 'Validé' };
const RENFORT_LABEL = { NON_PREVENU: 'Non prévenu', PREVENU: 'Prévenu', SUR_PLACE: 'Sur place' };

const esc = s => (s ?? '').toString().replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
const fmt = iso => iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
const fmtCoord = c => c && c.length === 6 ? c.slice(0, 3) + ' ' + c.slice(3) : (c || '—');
const renfortLabel = v => RENFORT_LABEL[v] ?? v;

/** Ouvre une fenêtre d'impression avec la fiche de l'intervention. */
export function exportInterventionPdf(i, journal, cris) {
  const row = (k, v) => `<tr><th>${k}</th><td>${esc(v) || '—'}</td></tr>`;
  // En cours : engins live. Clôturée : snapshot historisé, équipage en colonne (liste).
  const engins = i.enCours
    ? (i.engins.map(e => `${esc(e.libelle)} <span class="muted">(${esc(e.etatLabel)})</span>`).join(' · ') || '—')
    : ((i.enginsHisto ?? []).map(e => {
        const crew = e.equipage.length
          ? '<ul class="crew">' + e.equipage.map(m =>
              `<li>${esc(m.grade)} ${esc(m.nom)}${m.matricule ? ' <span class="muted">' + esc(m.matricule) + '</span>' : ''}${m.poste ? ' — ' + esc(m.poste) : ''}</li>`
            ).join('') + '</ul>'
          : '<p class="muted">Équipage non historisé</p>';
        return `<div class="engin-h"><div class="engin-t"><strong>${esc(e.libelle)}</strong>${e.typeCode ? ' <span class="muted">' + esc(e.typeCode) + '</span>' : ''}</div>${crew}</div>`;
      }).join('') || '—');
  const mc = journal.map(ev =>
    `<tr><td class="mono">${fmt(ev.creeLe)}</td><td>${esc(ev.message)}</td><td class="muted">${esc(ev.acteurUsername)}</td></tr>`).join('');
  const crisH = cris.map(c =>
    `<div class="cri"><h4>${esc(c.vehiculeLibelle)} — ${CRI_LABEL[c.statut] ?? c.statut}</h4>
      <p>${esc(c.contenu) || '<span class="muted">(vide)</span>'}</p>
      ${c.validePar ? `<p class="muted">Validé par ${esc(c.validePar)}</p>` : ''}</div>`).join('') || '<p class="muted">Aucun</p>';

  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${esc(i.code)}</title>
    <style>
      * { font-family: Arial, Helvetica, sans-serif; color: #1a1a1a; }
      body { margin: 28px; font-size: 12px; }
      h1 { font-size: 20px; margin: 0 0 4px; }
      .sub { color: #666; margin: 0 0 16px; }
      h3 { font-size: 13px; border-bottom: 1px solid #ccc; padding-bottom: 3px; margin: 18px 0 8px; }
      table { width: 100%; border-collapse: collapse; }
      .meta th { text-align: left; width: 130px; color: #666; font-weight: 600; padding: 3px 8px 3px 0; vertical-align: top; }
      .meta td { padding: 3px 0; }
      .mc th, .mc td { text-align: left; border-bottom: 1px solid #eee; padding: 4px 6px; font-size: 11px; }
      .mono { font-family: monospace; white-space: nowrap; }
      .muted { color: #888; }
      .cri { border: 1px solid #ddd; border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; }
      .cri h4 { margin: 0 0 4px; font-size: 12px; }
      .cri p { margin: 0; white-space: pre-wrap; }
      .engin-h { margin: 0 0 10px; }
      .engin-t { font-size: 12px; }
      ul.crew { margin: 3px 0 0; padding-left: 18px; }
      ul.crew li { font-size: 11px; color: #444; padding: 1px 0; }
      footer { margin-top: 24px; color: #999; font-size: 10px; }
      @media print { body { margin: 12mm; } }
    </style></head><body>
    <h1>${esc(i.code)} — ${esc(i.motif)}</h1>
    <p class="sub">${i.enCours ? 'En cours' : 'Clôturée'} · ${i.nature ? esc(i.nature.label) : ''}</p>
    <table class="meta">
      ${row('Début', fmt(i.debut))}
      ${row('Fin', i.fin ? fmt(i.fin) : '—')}
      ${row('Requérant', i.requerant)}
      ${row('Téléphone', i.telephone)}
      ${row('Commune', i.commune)}
      ${row('Coordonnées', fmtCoord(i.coordonnees))}
      ${row('Victimes', i.nbVictimes ?? '—')}
      ${row('Incendie', i.incendie ? 'Oui' : 'Non')}
      ${row('Véhicule impliqué', i.vehiculeImplique ? 'Oui' : 'Non')}
      ${row('Renfort GN', renfortLabel(i.renfortGn))}
      ${row('Renfort VINCI', renfortLabel(i.renfortVinci))}
      ${row('Observation', i.observation)}
      ${row('Créée par', i.creePar)}
    </table>
    <h3>Engins${i.enCours ? '' : ' &amp; équipage'}</h3>
    ${i.enCours ? `<p>${engins}</p>` : `<div class="engins-pdf">${engins}</div>`}
    <h3>Main courante</h3>
    <table class="mc"><tbody>${mc || '<tr><td class="muted">Aucun événement</td></tr>'}</tbody></table>
    <h3>Comptes rendus (CRI)</h3>${crisH}
    <footer>Nexis — fiche d'intervention ${esc(i.code)} · exportée le ${new Date().toLocaleString('fr-FR')}</footer>
    </body></html>`;

  const w = window.open('', '_blank');
  if (!w) { toast.error('Autorisez les pop-ups pour exporter en PDF.'); return; }
  w.document.write(html); w.document.close(); w.focus();
  setTimeout(() => w.print(), 300);
}

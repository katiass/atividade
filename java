// ==================== VERIFICAÇÃO DE LOCALSTORAGE ====================
function verificarLocalStorage() {
    try {
        const test = '__storage_test__';
        localStorage.setItem(test, test);
        localStorage.removeItem(test);
        return true;
    } catch (e) {
        return false;
    }
}

if (!verificarLocalStorage()) {
    alert('⚠️ O armazenamento local está desabilitado ou indisponível.\n\n' +
          'Seus dados NÃO serão salvos ao fechar o navegador.\n\n' +
          'Possíveis motivos:\n' +
          '- Modo anônimo/privado\n' +
          '- Configurações de privacidade do navegador\n' +
          '- Extensão bloqueando cookies\n\n' +
          'Recomendação: use o botão "Exportar" regularmente para fazer backup.');
}

// ==================== ARMAZENAMENTO ====================
const STORAGE_KEY = 'monitor_atividades_tarefas_v4';

function carregar() {
    if (!verificarLocalStorage()) return [];
    try {
        const dados = localStorage.getItem(STORAGE_KEY);
        return dados ? JSON.parse(dados) : [];
    } catch (e) {
        console.error('Erro ao carregar dados:', e);
        return [];
    }
}

function salvar(dados) {
    if (!verificarLocalStorage()) {
        toast('⚠️ Dados não salvos! Faça backup manual.');
        return;
    }
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(dados));
    } catch (e) {
        console.error('Erro ao salvar:', e);
        toast('❌ Erro ao salvar! Tente exportar seus dados.');
    }
}

function gerarId() { 
    return Date.now().toString(36) + Math.random().toString(36).substr(2, 5); 
}

let atividades = carregar();
let filtroAtivo = 'todas';
let atividadeAlvoTarefa = null;
let atividadeAlvoFinalizar = null;
let semanaOffset = 0;

function toast(msg) {
    const el = document.getElementById('toast');
    el.textContent = msg; 
    el.style.display = 'block';
    el.style.animation = 'none'; 
    el.offsetHeight;
    el.style.animation = 'slideUp 0.3s ease, fadeOut 0.3s ease 2.5s forwards';
    setTimeout(() => el.style.display = 'none', 2800);
}

function formatarDataHora(iso) {
    if (!iso) return '--';
    const d = new Date(iso);
    return `${String(d.getDate()).padStart(2,'0')}/${String(d.getMonth()+1).padStart(2,'0')}/${d.getFullYear()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function formatarHora(iso) {
    if (!iso) return '--';
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function calcularDuracaoMinutos(inicio, fim) {
    if (!inicio || !fim) return 0;
    const diff = new Date(fim) - new Date(inicio);
    return diff > 0 ? Math.round(diff / 60000) : 0;
}

function formatarDuracao(min) {
    if (!min || min <= 0) return '0 min';
    if (min < 60) return `${min} min`;
    const h = Math.floor(min / 60), m = min % 60;
    return m === 0 ? `${h}h` : `${h}h ${m}min`;
}

function duracaoTotalAtividade(atividade) {
    if (!atividade.tarefas || atividade.tarefas.length === 0) return 0;
    return atividade.tarefas.reduce((t, tf) => t + (tf.duracao || 0), 0);
}

// ---------- SEMANA ----------
function getInicioSemana(offset) {
    const agora = new Date();
    const diaSemana = agora.getDay();
    const diff = agora.getDate() - diaSemana + (diaSemana === 0 ? -6 : 1);
    const segunda = new Date(agora.setDate(diff));
    segunda.setHours(0,0,0,0);
    segunda.setDate(segunda.getDate() + (offset * 7));
    return segunda;
}

function getFimSemana(inicio) {
    const fim = new Date(inicio);
    fim.setDate(fim.getDate() + 6);
    fim.setHours(23,59,59,999);
    return fim;
}

function formatarDataSimples(d) {
    return `${String(d.getDate()).padStart(2,'0')}/${String(d.getMonth()+1).padStart(2,'0')}/${d.getFullYear()}`;
}

function diaSemanaNome(d) {
    const nomes = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
    return nomes[d.getDay()];
}

// ---------- LISTA ----------
function filtrar() {
    if (filtroAtivo === 'todas') return atividades;
    if (filtroAtivo === 'em-andamento') return atividades.filter(a => !a.finalizada);
    if (filtroAtivo === 'concluidas') return atividades.filter(a => a.finalizada);
    if (filtroAtivo === 'ganho') return atividades.filter(a => a.energia === 'ganho');
    if (filtroAtivo === 'dreno') return atividades.filter(a => a.energia === 'dreno');
    return atividades.filter(a => a.dominio === filtroAtivo);
}

function renderizarTarefasResumo(atividade) {
    if (!atividade.tarefas || atividade.tarefas.length === 0)
        return '<div class="tarefas-resumo"><div class="sem-tarefas">📭 Nenhuma tarefa ainda</div></div>';
    
    let h = '<div class="tarefas-resumo">';
    atividade.tarefas.forEach((t, i) => {
        h += `<div class="tarefa-item">
                <span class="tarefa-numero">${i+1}</span>
                <span class="tarefa-descricao">${t.descricao||'Tarefa'}</span>
                <span class="tarefa-periodo">${formatarHora(t.inicio)}→${formatarHora(t.fim)}</span>
                <span class="tarefa-duracao">⏱️${formatarDuracao(t.duracao)}</span>
                ${!atividade.finalizada ? `<button class="btn-remover-tarefa" onclick="removerTarefa('${atividade.id}','${t.id}')">✕</button>` : ''}
              </div>`;
    });
    const total = duracaoTotalAtividade(atividade);
    h += `<div class="tarefas-total">
            <span>📊 ${atividade.tarefas.length} tarefa(s)</span>
            <span class="total-valor">⏱️ ${formatarDuracao(total)}</span>
          </div></div>`;
    return h;
}

function renderizarLista() {
    const lista = filtrar();
    const grid = document.getElementById('activitiesGrid');
    
    if (lista.length === 0) {
        grid.innerHTML = '<div class="empty-state"><div class="empty-icon">📭</div><p>Nenhuma atividade</p></div>';
    } else {
        grid.innerHTML = lista.map(a => {
            const cls = a.finalizada ? `concluida-${a.energia||'neutral'}` : 'em-andamento';
            const dtFim = a.tarefas?.length ? a.tarefas.reduce((u,t)=>t.fim>u?t.fim:u, a.tarefas[0].fim) : null;
            return `<div class="activity-card ${cls}">
                        <div class="card-header">
                            <h3>${a.nome}</h3>
                            <span class="card-domain">${a.dominio}</span>
                        </div>
                        <div class="card-meta">
                            <span>📅 ${formatarDataHora(a.dataInicio)}</span>
                            ${a.finalizada && dtFim ? `<span>🏁 ${formatarDataHora(dtFim)}</span>` : ''}
                            <span>🔄${a.frequencia}</span>
                            <span class="badge badge-mode ${a.modo}">${a.modo}</span>
                            ${a.finalizada ? `<span class="badge badge-energy ${a.energia}">${a.energia==='ganho'?'⚡Ganho':a.energia==='dreno'?'🔋Dreno':'➖Neutro'}</span>` : ''}
                            <span class="badge badge-status ${a.finalizada?'concluida':'andamento'}">${a.finalizada?'✅Concluída':'🟡Em andamento'}</span>
                        </div>
                        ${renderizarTarefasResumo(a)}
                        ${a.aprendizado ? `<div class="card-notes">💡${a.aprendizado}</div>` : ''}
                        <div class="card-actions">
                            ${!a.finalizada ? `<button class="btn-xs btn-add-tarefa" onclick="abrirModalTarefa('${a.id}')">➕Tarefa</button>
                                             <button class="btn-xs btn-finalizar" onclick="abrirModalFinalizar('${a.id}')">🏁Finalizar</button>` : ''}
                            <button class="btn-xs btn-excluir" onclick="excluirAtividade('${a.id}')">🗑️Excluir</button>
                        </div>
                    </div>`;
        }).join('');
    }
    atualizarDashboard();
}

function atualizarDashboard() {
    const emAnd = atividades.filter(a=>!a.finalizada).length;
    const concl = atividades.filter(a=>a.finalizada);
    const tts = atividades.reduce((s,a)=>s+(a.tarefas?.length||0),0);
    const g = concl.filter(a=>a.energia==='ganho').length;
    const d = concl.filter(a=>a.energia==='dreno').length;
    
    document.getElementById('statEmAndamento').textContent = emAnd;
    document.getElementById('statTotalTarefas').textContent = tts;
    document.getElementById('statGain').textContent = concl.length>0?Math.round((g/concl.length)*100)+'%':'0%';
    document.getElementById('statDrain').textContent = concl.length>0?Math.round((d/concl.length)*100)+'%':'0%';
}

// ---------- LINHA DO TEMPO HORIZONTAL ----------
function gerarMarcacoesEixo() {
    let marcas = '';
    for (let h = 0; h <= 24; h += 2) {
        const left = (h / 24) * 100;
        marcas += `<div class="timeline-eixo-marcacao" style="left:${left}%;">${h}h</div>`;
        marcas += `<div class="timeline-eixo-linha" style="left:${left}%;"></div>`;
    }
    return marcas;
}

function renderizarTimeline() {
    const container = document.getElementById('timelineContainer');
    const inicioSemana = getInicioSemana(semanaOffset);
    const fimSemana = getFimSemana(inicioSemana);
    document.getElementById('semanaLabel').textContent = `${formatarDataSimples(inicioSemana)} → ${formatarDataSimples(fimSemana)}`;

    const tarefasSemana = [];
    atividades.forEach(atv => {
        if (atv.tarefas) {
            atv.tarefas.forEach(t => {
                const d = new Date(t.inicio);
                if (d >= inicioSemana && d <= fimSemana) {
                    tarefasSemana.push({
                        ...t, 
                        atividadeNome: atv.nome, 
                        atividadeId: atv.id, 
                        modo: atv.modo, 
                        energia: atv.energia, 
                        finalizada: atv.finalizada, 
                        dominio: atv.dominio
                    });
                }
            });
        }
    });

    if (tarefasSemana.length === 0) {
        container.innerHTML = '<div class="empty-state"><div class="empty-icon">📊</div><p>Nenhuma tarefa nesta semana</p></div>';
        return;
    }

    const diasMap = {};
    tarefasSemana.forEach(t => {
        const d = new Date(t.inicio);
        const chave = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
        if (!diasMap[chave]) diasMap[chave] = { data: d, tarefas: [] };
        diasMap[chave].tarefas.push(t);
    });

    const diasOrdenados = Object.values(diasMap).sort((a,b) => a.data - b.data);
    let html = '';
    
    diasOrdenados.forEach(dia => {
        const totalMinDia = dia.tarefas.reduce((s,t) => s + (t.duracao||0), 0);
        dia.tarefas.sort((a,b) => new Date(a.inicio) - new Date(b.inicio));
        
        html += `
        <div class="timeline-day">
            <div class="timeline-day-header">
                <span class="dia-semana">${diaSemanaNome(dia.data)}</span>
                <span class="data-dia">${formatarDataSimples(dia.data)}</span>
                <span class="total-dia">⏱️ ${formatarDuracao(totalMinDia)}</span>
            </div>
            <div class="timeline-eixo">${gerarMarcacoesEixo()}</div>
            <div class="timeline-tracks">
                ${dia.tarefas.map(t => {
                    const ini = new Date(t.inicio);
                    const minutosInicio = ini.getHours() * 60 + ini.getMinutes();
                    const duracao = t.duracao || 0;
                    const leftPct = (minutosInicio / 1440) * 100;
                    const widthPct = (duracao / 1440) * 100;
                    const modoClass = `modo-${t.modo}`;
                    const energiaClass = t.finalizada ? `energia-${t.energia||'neutro'}` : 'em-andamento';
                    const label = `${t.atividadeNome}: ${t.descricao||'Tarefa'} (${formatarHora(t.inicio)}→${formatarHora(t.fim)})`;
                    return `<div class="timeline-track">
                                <div class="timeline-bar-h ${modoClass} ${energiaClass}" 
                                     style="left:${leftPct}%; width:${Math.max(widthPct, 0.5)}%;" 
                                     title="${label}">
                                    ${t.descricao || t.atividadeNome}
                                </div>
                            </div>`;
                }).join('')}
            </div>
        </div>`;
    });
    container.innerHTML = html;
}

// ==================== EXPORTAÇÃO / IMPORTAÇÃO ====================
function exportarDados() {
    const blob = new Blob([JSON.stringify(atividades, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backup_atividades_${new Date().toISOString().slice(0,10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast('💾 Backup exportado!');
}

function importarDados() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                const dados = JSON.parse(event.target.result);
                if (!Array.isArray(dados)) throw new Error('Formato inválido');
                atividades = dados;
                salvar(atividades);
                renderizarLista();
                renderizarTimeline();
                toast('📥 Dados restaurados com sucesso!');
            } catch (err) {
                toast('❌ Arquivo inválido. Selecione um backup .json válido.');
            }
        };
        reader.readAsText(file);
    };
    input.click();
}

// ==================== CRUD E EVENTOS ====================
document.querySelectorAll('#modePillsAtividade .mode-pill').forEach(p => {
    p.addEventListener('click', () => {
        document.querySelectorAll('#modePillsAtividade .mode-pill').forEach(x => x.classList.remove('active'));
        p.classList.add('active');
        document.getElementById('modoAtividade').value = p.dataset.mode;
    });
});

document.getElementById('formNovaAtividade').addEventListener('submit', e => {
    e.preventDefault();
    const nome = document.getElementById('nomeAtividade').value.trim();
    const inicio = document.getElementById('dataInicioAtividade').value;
    const modo = document.getElementById('modoAtividade').value;
    const dominio = document.getElementById('dominioAtividade').value;
    const freq = document.getElementById('frequenciaAtividade').value;
    
    if (!nome || !inicio || !dominio) return toast('⚠️ Preencha os campos obrigatórios.');
    
    atividades.unshift({ 
        id: gerarId(), 
        nome, 
        dataInicio: inicio, 
        modo, 
        dominio, 
        frequencia: freq, 
        tarefas: [], 
        finalizada: false, 
        energia: null, 
        aprendizado: null 
    });
    
    salvar(atividades);
    renderizarLista();
    renderizarTimeline();
    document.getElementById('formNovaAtividade').reset();
    document.getElementById('modoAtividade').value = 'superficial';
    document.querySelectorAll('#modePillsAtividade .mode-pill').forEach(x => x.classList.remove('active'));
    document.querySelector('#modePillsAtividade .mode-pill[data-mode="superficial"]').classList.add('active');
    document.getElementById('frequenciaAtividade').value = 'Semanal';
    toast('▶️ Atividade criada!');
});

window.abrirModalTarefa = function(id) {
    atividadeAlvoTarefa = atividades.find(a => a.id === id);
    if (!atividadeAlvoTarefa) return;
    document.getElementById('modalTarefaAtividadeNome').textContent = atividadeAlvoTarefa.nome;
    document.getElementById('tarefaDescricao').value = '';
    const agora = new Date();
    const off = agora.getTimezoneOffset();
    const iso = new Date(agora.getTime() - off * 60000).toISOString().slice(0, 16);
    document.getElementById('tarefaInicio').value = iso;
    document.getElementById('tarefaFim').value = '';
    document.getElementById('modalTarefa').style.display = 'flex';
};

window.fecharModalTarefa = function() { 
    document.getElementById('modalTarefa').style.display = 'none'; 
    atividadeAlvoTarefa = null; 
};

document.getElementById('btnSalvarTarefa').addEventListener('click', () => {
    if (!atividadeAlvoTarefa) return;
    const d = document.getElementById('tarefaDescricao').value.trim();
    const ini = document.getElementById('tarefaInicio').value;
    const fim = document.getElementById('tarefaFim').value;
    
    if (!d || !ini || !fim) return toast('⚠️ Preencha todos os campos.');
    const dur = calcularDuracaoMinutos(ini, fim);
    if (dur <= 0) return toast('⚠️ Término deve ser posterior ao início.');
    
    if (!atividadeAlvoTarefa.tarefas) atividadeAlvoTarefa.tarefas = [];
    atividadeAlvoTarefa.tarefas.push({ id: gerarId(), descricao: d, inicio: ini, fim, duracao: dur });
    salvar(atividades);
    fecharModalTarefa();
    renderizarLista();
    renderizarTimeline();
    toast(`➕ Tarefa adicionada (${formatarDuracao(dur)})`);
});

document.getElementById('modalTarefa').addEventListener('click', function(e) { 
    if (e.target === this) fecharModalTarefa(); 
});

window.removerTarefa = function(atvId, tarId) {
    const atv = atividades.find(a => a.id === atvId);
    if (!atv || atv.finalizada) return;
    if (confirm('Remover esta tarefa?')) { 
        atv.tarefas = atv.tarefas.filter(t => t.id !== tarId); 
        salvar(atividades); 
        renderizarLista(); 
        renderizarTimeline(); 
        toast('🗑️ Tarefa removida.'); 
    }
};

window.abrirModalFinalizar = function(id) {
    atividadeAlvoFinalizar = atividades.find(a => a.id === id);
    if (!atividadeAlvoFinalizar) return;
    document.getElementById('modalFinalizarNome').textContent = atividadeAlvoFinalizar.nome;
    document.getElementById('modalFinalizarTarefas').textContent = (atividadeAlvoFinalizar.tarefas || []).length;
    document.getElementById('modalFinalizarDuracao').textContent = formatarDuracao(duracaoTotalAtividade(atividadeAlvoFinalizar));
    document.getElementById('finalizarAprendizado').value = '';
    document.querySelectorAll('input[name="finalizarEnergia"]').forEach(r => r.checked = false);
    document.getElementById('modalFinalizar').style.display = 'flex';
};

window.fecharModalFinalizar = function() { 
    document.getElementById('modalFinalizar').style.display = 'none'; 
    atividadeAlvoFinalizar = null; 
};

document.getElementById('btnConfirmarFinalizacao').addEventListener('click', () => {
    if (!atividadeAlvoFinalizar) return;
    const en = document.querySelector('input[name="finalizarEnergia"]:checked');
    if (!en) return toast('⚠️ Selecione a energia.');
    
    atividadeAlvoFinalizar.energia = en.value;
    atividadeAlvoFinalizar.aprendizado = document.getElementById('finalizarAprendizado').value.trim() || null;
    atividadeAlvoFinalizar.finalizada = true;
    salvar(atividades);
    fecharModalFinalizar();
    renderizarLista();
    renderizarTimeline();
    toast('✅ Atividade finalizada!');
});

document.getElementById('modalFinalizar').addEventListener('click', function(e) { 
    if (e.target === this) fecharModalFinalizar(); 
});

window.excluirAtividade = function(id) {
    if (confirm('Excluir esta atividade e suas tarefas?')) { 
        atividades = atividades.filter(a => a.id !== id); 
        salvar(atividades); 
        renderizarLista(); 
        renderizarTimeline(); 
        toast('🗑️ Atividade excluída.'); 
    }
};

// Atividade Rápida
document.querySelectorAll('#rapidaModePills .mode-pill').forEach(p => {
    p.addEventListener('click', () => {
        document.querySelectorAll('#rapidaModePills .mode-pill').forEach(x => x.classList.remove('active'));
        p.classList.add('active');
        document.getElementById('rapidaModo').value = p.dataset.mode;
    });
});

document.getElementById('btnAtividadeRapida').addEventListener('click', () => {
    document.getElementById('rapidaNome').value = ''; 
    document.getElementById('rapidaInicio').value = ''; 
    document.getElementById('rapidaFim').value = '';
    document.querySelectorAll('input[name="rapidaEnergia"]').forEach(r => r.checked = false);
    document.getElementById('rapidaModo').value = 'superficial';
    document.querySelectorAll('#rapidaModePills .mode-pill').forEach(x => x.classList.remove('active'));
    document.querySelector('#rapidaModePills .mode-pill[data-mode="superficial"]').classList.add('active');
    document.getElementById('modalRapida').style.display = 'flex';
});

window.fecharModalRapida = function() { 
    document.getElementById('modalRapida').style.display = 'none'; 
};

document.getElementById('btnSalvarRapida').addEventListener('click', () => {
    const n = document.getElementById('rapidaNome').value.trim();
    const ini = document.getElementById('rapidaInicio').value;
    const fim = document.getElementById('rapidaFim').value;
    const en = document.querySelector('input[name="rapidaEnergia"]:checked');
    const mod = document.getElementById('rapidaModo').value;
    const dom = document.getElementById('rapidaDominio').value;
    const fr = document.getElementById('rapidaFrequencia').value;
    
    if (!n || !ini || !fim || !en) return toast('⚠️ Preencha todos os campos.');
    const dur = calcularDuracaoMinutos(ini, fim);
    if (dur <= 0) return toast('⚠️ Término posterior ao início.');
    
    atividades.unshift({ 
        id: gerarId(), 
        nome: n, 
        dataInicio: ini, 
        modo: mod, 
        dominio: dom, 
        frequencia: fr, 
        tarefas: [{ id: gerarId(), descricao: n, inicio: ini, fim, duracao: dur }], 
        finalizada: true, 
        energia: en.value, 
        aprendizado: null 
    });
    
    salvar(atividades);
    fecharModalRapida();
    renderizarLista();
    renderizarTimeline();
    toast('✅ Atividade rápida registrada!');
});

document.getElementById('modalRapida').addEventListener('click', function(e) { 
    if (e.target === this) fecharModalRapida(); 
});

// Exportar / Importar
document.getElementById('btnExportar').addEventListener('click', exportarDados);
document.getElementById('btnImportar').addEventListener('click', importarDados);

// Limpar tudo
document.getElementById('btnLimparTudo').addEventListener('click', () => {
    if (atividades.length === 0) return toast('📭 Nada para limpar.');
    if (confirm('⚠️ Excluir TODAS as atividades?')) { 
        atividades = []; 
        salvar(atividades); 
        renderizarLista(); 
        renderizarTimeline(); 
        toast('🗑️ Tudo removido.'); 
    }
});

// Filtros
document.querySelectorAll('#filterPills .filter-pill').forEach(p => {
    p.addEventListener('click', () => {
        document.querySelectorAll('#filterPills .filter-pill').forEach(x => x.classList.remove('active'));
        p.classList.add('active');
        filtroAtivo = p.dataset.filter;
        renderizarLista();
    });
});

// Tabs
document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        const target = tab.dataset.tab;
        if (target === 'lista') document.getElementById('tabLista').classList.add('active');
        if (target === 'timeline') { 
            document.getElementById('tabTimeline').classList.add('active'); 
            renderizarTimeline(); 
        }
    });
});

// Navegação semana
document.getElementById('btnSemanaAnterior').addEventListener('click', () => { 
    semanaOffset--; 
    renderizarTimeline(); 
});

document.getElementById('btnProximaSemana').addEventListener('click', () => { 
    semanaOffset++; 
    renderizarTimeline(); 
});

document.getElementById('btnSemanaAtual').addEventListener('click', () => { 
    semanaOffset = 0; 
    renderizarTimeline(); 
});

// Inicialização
const agora = new Date();
const off = agora.getTimezoneOffset();
document.getElementById('dataInicioAtividade').value = new Date(agora.getTime() - off * 60000).toISOString().slice(0, 16);
renderizarLista();
renderizarTimeline();
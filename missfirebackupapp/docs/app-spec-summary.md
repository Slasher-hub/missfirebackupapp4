# Especificação consolidada do app (para mockup do app de acompanhamento)

Objetivo: compilar todas as entidades, campos, listas fixas e eventos/filtros necessários para o ChatGPT gerar um mockup de um app de acompanhamento dos eventos deste app.

## Metadados do projeto
- App ID: com.example.missfirebackupapp
- Versão: 1.0.1-test (versionCode 2)
- SDK: minSdk 23, target/compileSdk 35
- Principais libs: Room 2.6.1, Firebase (Auth, Firestore, Storage), Coroutines, Play Services Location

## Telas e fluxos
- Login: autenticação Firebase (anônima no sync; tela com email/senha também existe).
- Menu principal (MainActivity):
  - Ações: Registrar Backup, Registrar Misfire.
  - Lista de histórico com alternância entre Backup e Misfire.
  - Filtros multisseleção por mês/ano, unidade e cava (Backup) e por mês/ano e local (Misfire).
  - Indicador de pendências (itens não sincronizados).
- Registro de Backup (BackupActivity):
  - Campos e listas.
  - Captura de até 3 fotos com coordenadas e preferência de sistema de coordenadas.
  - Salva localmente como INCOMPLETO.
- Detalhe de Backup (BackupDetailActivity):
  - Edição dos campos enquanto INCOMPLETO.
  - Finalizar (marca PRONTO) e tentar sincronização.
  - Visualização das fotos com metadados (lat/lon/alt/sistema/dataHora).
- Registro de Misfire (MisfireActivity):
  - Campos básicos: data, local, responsável, itens encontrados, descrição.
  - Anexos: fotos (até 5) e arquivos (opcional, mantido para detalhe).
  - Salva localmente; pode abrir para edição.
  - Permite adicionar atualizações e anexos a atualizações (no detalhe).
- Detalhe de Misfire (MisfireDetailActivity):
  - Resumo inicial com contagens de fotos/arquivos.
  - Lista de atualizações em ordem cronológica com chips de status (Local/Nuvem/Concluída).
  - Adicionar atualização + anexos (foto/arquivo) e sincronizar.
  - Concluir investigação (causa e medidas preventivas) e sincronizar.

## Preferência de sistema de coordenadas
- Valores possíveis (armazenado em SharedPreferences "prefs.coordSystem"):
  - WGS84 (padrão)
  - SIRGAS2000-21S, -22S, -23S, -24S
  - SAD69-21S, -22S, -23S, -24S
- Conversão utilizada para exibição e armazenamento quando há GPS:
  - WGS84: X=lat, Y=lon, Z=alt
  - SIRGAS/SAD: aplica adjustDatum (offset simples) e converte para UTM (X=easting, Y=northing), Z=alt
  - Sem GPS: usuário pode informar manualmente (especialmente quando "Sem fotos" estiver marcado em Backup).

## Entidades (Room) e campos

### BackupEntity (backup_table)
- id: Int (PK)
- remoteId: String? (Firestore doc id)
- data: String (formato dd/MM/yyyy)
- unidade: String
- cava: String
- banco: String
- fogoId: String
- furoNumero: String
- detonadorNumero: String
- espoletaId: String
- motivo: String
- tipoDetonador: String
- caboDetonador: String
- metragem: String
- tentativaRecuperacao: String
- coordenadaX: Double
- coordenadaY: Double
- coordenadaZ: Double
- sistemaCoordenadas: String (ex.: WGS84, SIRGAS2000-23S)
- status: String [INCOMPLETO | PRONTO | SINCRONIZADO]
- syncError: Boolean
- syncErrorMessage: String?
- createdAt: Long
- lastSyncAt: Long?

### FotoEntity (foto_table)
- id: Int (PK)
- backupId: Int (FK -> BackupEntity)
- caminhoFoto: String
- remoteUrl: String?
- latitude: Double?
- longitude: Double?
- altitude: Double?
- sistemaCoordenadas: String?
- dataHora: String (yyyy-MM-dd HH:mm:ss)

### MisfireEntity (missfire_table)
- id: Int (PK)
- remoteId: String?
- dataOcorrencia: Long (epoch millis)
- local: String
- responsavel: String
- itensEncontrados: String
- descricaoOcorrencia: String
- statusInvestigacao: String [EM_ANDAMENTO | CONCLUIDA]
- dataDesmonte: Long?
- causa: String?
- infoAdicionais: String?
- medidasPreventivas: String?
- createdAt: Long
- lastUpdated: Long
- syncError: Boolean
- syncErrorMessage: String?
- lastSyncAt: Long?

### MissfireUpdateEntity (missfire_update_table)
- id: Int (PK)
- missfireId: Int (FK)
- userId: String?
- texto: String
- createdAt: Long
- remoteId: String?
- lastSyncAt: Long?
- syncError: Boolean
- syncErrorMessage: String?

### MissfireAttachmentEntity (missfire_attachment_table)
- id: Int (PK)
- missfireId: Int (FK)
- updateId: Int? (FK soft)
- tipo: String [FOTO | ARQUIVO]
- localPath: String (pode ser file:// ou content://)
- remoteUrl: String?
- mimeType: String
- tamanhoBytes: Long
- createdAt: Long

## Listas fixas (para filtros e formulários)
- Unidades/Locais (mesma lista usada em Backup e Misfire):
  - US Anglo American, US Atlantic Nickel, US CSN, US CMOC - Nióbio, US CMOC - Fosfato, US MVV, US Colomi, US Maracá, US Cajati, US Taboca, US Vanádio, Usiminas, US Ciplan, US Almas, US Belocal - Matozinhos, US Belocal - Limeira, US Caraíba - Pilar, US Caraíba - Vermelhos, US Oz Minerals, US Jacobina, US Anglo Gold Ashanti - Crixás, US Aripuanã, Ferbasa, Vale Urucum, US Carajás, US S11D, US Sossego, Vale Onça Puma, US Vale Sul - Itabira, US Vale Sul - Mariana, US Vale Sul - Brucutu, US Vale Sul - CPX, US Vale Sul - Vargem grande, US Vale Sul - Água Limpa, US Viga, US Morro da Mina, CD São Paulo, CD Jardinópolis, CD Minas Gerais, CD Paraná, CD Bahia, CD Goiás, CD Pernambuco, CD Rio Grande do Sul, PD Matriz, N/A, CD NOVA ROMA
- Backup.motivo:
  - Fuga excessiva/Cabo cortado (Tamponamento), (Bombeamento), (Movimentação de Equip.), (Queda de material externo), (Queda de material interno), (Vazamento de carga), (Abatimento de tampão), Descarga atmosférica, Queda de escorva no furo, Detonador com defeito (Descreva nas Obs.), Outro (Descreva nas Obs.)
- Tipo de detonador: SP, UG, OP, XD, SW, SP - DT5, WP
- Cabo detonador: Standard - STD, Reforçado - HD, Reforçado - HD2, Super reforçado - XO, Super reforçado - M95, Super reforçado - M105, UG2 - SW, UG3 - SW, WP - DT5
- Metragem: 6, 8, 10, 15, 20, 30, 40, 60, 0
- Tentativa de recuperação: Sim, Não

## Filtros e dimensões de acompanhamento
- Backups:
  - Mês/Ano: derivado de data (formato dd/MM/yyyy -> MM/yyyy)
  - Unidade: valores da lista fixa
  - Cava: texto livre (distintos existentes)
  - Status: INCOMPLETO | PRONTO | SINCRONIZADO (pode ser derivado para contagens)
  - Pendências: syncError (bool)
- Misfire:
  - Mês/Ano: derivado de dataOcorrencia (epoch -> MM/yyyy)
  - Local (unidade): valores da lista fixa
  - Status: EM_ANDAMENTO | CONCLUIDA
  - Pendências: syncError (bool) e updates sem remoteId

## Eventos/ações relevantes (para mockup)
- Backup
  - Registro criado (INCOMPLETO)
  - Fotos adicionadas/removidas (0..3), com metadados GPS
  - Finalização -> status PRONTO
  - Sincronização bem-sucedida -> status SINCRONIZADO, preenchido remoteId, photoUrls
  - Sincronização falhou -> syncError=true, syncErrorMessage
  - Exclusão local (com cascade das fotos)
- Misfire
  - Registro criado/atualizado
  - Adição de fotos/arquivos na base (sem update) e por atualização específica
  - Nova atualização (texto), possível edição do texto enquanto não sincronizada
  - Sincronização geral (missfire + uploads + updates)
  - Conclusão de investigação (causa, medidas)
  - Exclusão local

## Estruturas de payload (para app de acompanhamento)

### Firestore: backups/{remoteId}
Exemplo de documento:
{
  "id": 12,
  "remoteId": "f1a2b3c4-...",
  "data": "11/09/2025",
  "unidade": "US Vale Sul - Brucutu",
  "cava": "CV-03",
  "banco": "B1",
  "fogoId": "FG-2025-09-11",
  "furoNumero": "F-120",
  "detonadorNumero": "D-55",
  "espoletaId": "ESP-998",
  "motivo": "Descarga atmosférica",
  "tipoDetonador": "SP",
  "caboDetonador": "HD",
  "metragem": "15",
  "tentativaRecuperacao": "Não",
  "coordenadaX": -19.91234,
  "coordenadaY": -43.93778,
  "coordenadaZ": 732.5,
  "sistemaCoordenadas": "WGS84",
  "status": "SINCRONIZADO",
  "createdAt": 1726051200000,
  "createdByUserId": "uid-or-null",
  "appVersionCode": 2,
  "appVersionName": "1.0.1-test",
  "device": "Samsung SM-A515F",
  "installationId": "uuid...",
  "photoUrl": "https://.../photos/1.jpg",
  "photoUrls": ["https://.../1.jpg", "https://.../2.jpg"],
  "photoList": [
    {
      "url": "https://.../1.jpg",
      "latitude": -19.91234,
      "longitude": -43.93778,
      "altitude": 732.5,
      "sistemaCoordenadas": "WGS84",
      "dataHora": "2025-09-11 15:10:22",
      "uploadError": null,
      "uploadErrorDetail": null,
      "sizeBytes": 2451234
    }
  ]
}

Sugestão de coleções auxiliares para o app de acompanhamento: índices por mês/ano e por unidade para consultas rápidas.

### Firestore: missfires/{remoteId}
Documento principal:
{
  "localId": 7,
  "remoteId": "uuid",
  "dataOcorrencia": 1726051200000,
  "local": "US Vale Sul - Brucutu",
  "responsavel": "Fulano",
  "itensEncontrados": "1 cartucho, 2 estopins",
  "descricaoOcorrencia": "Falha de detonação no furo F-120",
  "statusInvestigacao": "EM_ANDAMENTO",
  "causa": null,
  "medidasPreventivas": null,
  "createdAt": 1726051100000,
  "lastUpdated": 1726051300000,
  "createdByUserId": "uid-or-null",
  "appVersionCode": 2,
  "appVersionName": "1.0.1-test",
  "device": "Samsung SM-A515F",
  "installationId": "uuid...",
  "photoUrls": ["https://.../p1.jpg"],
  "attachments": [
    {"id": 1, "tipo": "FOTO", "mimeType": "image/jpeg", "remoteUrl": "https://...", "tamanhoBytes": 123456, "createdAt": 1726051205000}
  ]
}

Subcoleção: missfires/{remoteId}/updates/{updateId}
{
  "texto": "Equipe isolou a área e sinalizou",
  "createdAt": 1726052200000,
  "userId": "uid-or-null"
}

## Regras/validações (negócio)
- Backup
  - Para salvar INCOMPLETO: requer Data, Unidade, Cava e (pelo menos 1 foto) OU (marcar "Sem fotos" e informar coordenadas X/Y).
  - Para finalizar (PRONTO): todos os campos preenchidos e coordenadas X/Y informadas.
  - Limite de 3 fotos por Backup.
- Misfire
  - Para salvar: data, local, responsável, itens encontrados, descrição.
  - Limite de 5 fotos na tela de registro; anexos adicionais por atualização no detalhe (limite 10 por operação de inclusão).
  - Conclusão requer causa e medidas preventivas.

## KPIs e visões sugeridas para o app de acompanhamento
- Backups por mês/unidade/cava, com breakdown por status (INCOMPLETO/PRONTO/SINCRONIZADO).
- Taxa de sucesso de upload (contar photoList.uploadError null vs não-null).
- Mapa com pontos dos Backups (coordenadas conforme sistema, preferencialmente convergindo para WGS84).
- Misfires por mês/unidade, status e tempo médio até conclusão (lastUpdated - createdAt).
- Lista de Misfires com badge de pendências (syncError ou updates locais).

## Glossário de estados
- Backup.status: INCOMPLETO (rascunho), PRONTO (pronto para sync), SINCRONIZADO (enviado).
- Misfire.statusInvestigacao: EM_ANDAMENTO, CONCLUIDA.

## Observações técnicas
- Uploads resilientes com até 3 tentativas e backoff.
- Auth anônima é usada automaticamente no Sync.
- Preferência de sistema de coordenadas persiste em SharedPreferences e afeta exibição e armazenamento dos campos.


# 📦 AbsolutEvents

Sistema completo de eventos automáticos para servidores **Paper/Spigot 1.21+**, desenvolvido em **Java 21**, focado em **performance, customização extrema e escalabilidade**.

---

## 🚀 Sobre o Plugin

O **AbsolutEvents** é uma solução completa para gerenciamento de eventos competitivos, oferecendo:

* 🎮 +30 tipos de eventos prontos
* 🏆 Sistema de torneios integrado (XLTournaments)
* 📊 Sistema de liga de eventos
* 🔌 Integração com múltiplos plugins
* 🗄️ Persistência em SQLite/MySQL
* ⚡ Alta performance e segurança contra exploits

---

## 📋 Requisitos

* Paper / Spigot 1.21+
* Java 21
* Vault + Economy plugin
* PlaceholderAPI (opcional)
* WorldEdit / FAWE (opcional)

---

## 🎮 Eventos Disponíveis

### 🟦 Movimento / Plataforma

* Spleef
* TNT Run
* Splegg
* Block Party
* Rainbow Run
* Campo Minado
* Semáforo
* Frog
* Anvil
* Fall

---

### ⚔️ Combate (PvP)

* Killer
* Killer Ponto
* Fight (1v1)
* Torneio (chaveamento)
* Sumo
* Hunter
* Paintball
* Guerra (times)
* Morte Súbita
* Team Deathmatch (TDM)
* KOTH
* Nexus
* **Battle Royale** 🟡
* Corrida Armada
* Super Smackers
* Thor

---

### 🏹 Destaques avançados

#### 🟡 Battle Royale

* Sistema de zona dinâmica (fechamento)
* Último sobrevivente vence
* Controle completo da arena
* Ideal para eventos competitivos grandes

#### ⚔️ Guerra de Clans (SimpleClans)

* Integração com **SimpleClans**
* Clans competem entre si
* Sistema de pontuação por equipe
* Eventos totalmente integrados ao sistema de clãs

---

### 🏁 Parkour / Corrida

* Sign (Parkour avançado)
* Montaria (corrida com cavalos)

---

### 💬 Eventos de Chat

* Votação
* Loteria
* Bolão
* Matemática
* Palavra
* Fast Click
* Sorteio
* Quiz

---

### 🎲 Eventos Especiais

* Batata Quente

---

## ⚙️ Funcionalidades

### 🎯 Sistema de Eventos

* Entrada e saída de jogadores
* Modo espectador
* Sistema de times
* Eventos simultâneos
* Totalmente configurável via YAML

---

### ⏰ AutoStart

* Eventos automáticos por horário
* Suporte a dias da semana
* Rotação e sorteio automático
* Múltiplos eventos simultâneos

---

### 🏆 Sistema de Torneios

Integração completa com XLTournaments.

#### Placeholder principal:

```yaml
objective: PLACEHOLDERAPI;absolutevents_wins_tournament
```

#### Placeholders:

* `%absolutevents_wins_tournament%` → vitórias no torneio atual
* `%absolutevents_total_wins%` → vitórias totais

#### Reset:

```bash
/evento resettournamentwins
```

---

### 🥇 Sistema de Liga

* Pontuação por eventos vencidos
* Integração com sistemas externos (ex: ligas)
* Compatível com comandos customizados:

```yaml
- liga givepoints @winner 15
```

---

### 📊 Ranking e Top

* Ranking em tempo real
* Top 3 automático
* Placeholders:

  * `@top1`, `@top2`, `@top3`
  * `@kills`, `@points`

---

### 📊 Actionbar Dinâmico

Exibe informações em tempo real:

```
Azul: 3/10 | Vermelho: 5/10
```

* 100% configurável
* Sem hardcode
* Suporte a placeholders

---

### 🎁 Sistema de Recompensas

* Vault (economia)
* Comandos customizados
* Integração com qualquer plugin

```yaml
Rewards:
  Commands:
    - eco give @winner 50000
    - liga givepoints @winner 15
```

---

### 🎒 Sistema de Inventário (ANTI-DUPE)

* Snapshot completo do jogador:

  * Inventário
  * Armadura
  * Offhand
  * EnderChest
  * XP / Vida / Fome
* Restauração automática
* Backup em arquivo
* Proteção contra duplicação

---

### 🧱 Restauração de Arena

* Suporte a schematics
* Compatível com:

  * WorldEdit
  * FAWE

---

### 🗄️ Banco de Dados

Suporte a:

* SQLite (padrão)
* MySQL (HikariCP)

Armazena:

* Vitórias totais
* Vitórias de torneio
* Participações

---

### 🧩 Itens Customizados

Suporte completo a:

* Vanilla
* **ItemsAdder**
* **MMOItems**

---

### 🌐 BungeeCord

* Eventos multi-servidor
* Sincronização de jogadores
* Execução remota de comandos

---

## 🔌 Integrações

* PlaceholderAPI
* Vault
* LuckPerms
* ItemsAdder
* MMOItems
* SimpleClans
* XLTournaments

---

## 🔧 Comandos

| Comando                       | Descrição            |
| ----------------------------- | -------------------- |
| `/evento`                     | Menu principal       |
| `/evento start <evento>`      | Inicia evento        |
| `/evento stop`                | Para evento          |
| `/evento reload`              | Recarrega configs    |
| `/evento update`              | Verifica atualização |
| `/evento update confirm`      | Atualiza plugin      |
| `/evento resettournamentwins` | Reseta torneio       |
| `/evento setup <evento>`      | Configuração         |

---

## 🔑 Permissões

| Permissão              | Função              |
| ---------------------- | ------------------- |
| `absolutevents.admin`  | Administração total |
| `absolutevents.evento` | Participar          |

---

## 📁 Estrutura

```
plugins/AbsolutEvents/
├── config.yml
├── storage.db
├── parkour-times.yml
├── quit-cache.yml
├── eventos/
├── menus/
└── playerdata/
```

---

## 📊 Performance

* Otimizado para alto número de jogadores
* Uso eficiente de threads e tasks
* Baixo impacto no TPS

---

## 📦 Dependências Internas

* XSeries
* HikariCP
* Gson
* ColorUtils
* InventoryAPI
* bStats

---

## ⚠️ Observações

* Não usar `/reload`
* Use `/evento reload`

---

## 👨‍💻 Autor

**AbsolutGG / Saynt**

---

## 📄 Licença

MIT License

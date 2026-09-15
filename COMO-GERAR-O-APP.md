# Gerar o app Android com o widget (passo a passo)

Você vai usar o **GitHub** como uma "fábrica" gratuita: sobe o projeto lá, ele compila o app (`.apk`) sozinho na nuvem, e você baixa pronto. **Não precisa instalar nada de programação no seu PC.**

Tempo total: ~15 minutos (na maior parte é só esperar a compilação).

---

## Parte 1 — Colocar o projeto no GitHub

### 1. Ter uma conta no GitHub
Se ainda não tem: acesse **https://github.com** → **Sign up** (é grátis). Se já tem, é só entrar.

### 2. Criar um repositório
1. No canto superior direito, clique no **+** → **New repository**.
2. **Repository name:** `card-tarefas-android` (ou o nome que quiser).
3. Deixe como **Public** (mais simples) ou **Private** — os dois funcionam.
4. Marque a opção **Add a README file**.
5. Clique em **Create repository**.

### 3. Subir os arquivos do projeto
1. No seu PC, **extraia** o arquivo `CardTarefasAndroid.zip` (botão direito → Extrair tudo). Vai aparecer uma pasta `CardTarefasAndroid`.
2. **Entre** nessa pasta. Você deve ver, lá dentro: `app`, `gradle`, `.github`, `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, etc.
3. No GitHub, dentro do seu repositório, clique em **Add file** → **Upload files**.
4. Selecione **tudo o que está dentro** da pasta `CardTarefasAndroid` (abra a pasta, aperte **Ctrl+A** para selecionar tudo) e **arraste** para a área de upload do GitHub.
   > ⚠️ Importante: arraste o **conteúdo** da pasta (o `app`, o `build.gradle.kts` etc. têm que ficar na **raiz** do repositório), e **não** a pasta `CardTarefasAndroid` inteira por fora.
5. Espere os arquivos aparecerem na lista (inclusive a pasta `.github`) e clique em **Commit changes**.

---

## Parte 2 — A fábrica compila sozinha

1. No repositório, clique na aba **Actions** (no topo).
2. Você verá uma execução chamada **"Compilar APK"** rodando (bolinha amarela girando). Isso leva **3 a 5 minutos**.
3. Quando ficar com um **✓ verde**, clique nessa execução.
4. Role até o final, na seção **Artifacts**, e baixe o **`CardTarefas-APK`** (vem como um `.zip`).
5. **Extraia** esse zip → dentro está o **`app-debug.apk`**. Esse é o seu app. 🎉

> Se a aba Actions estiver vazia (não começou a compilar), veja a seção **"Se algo der errado"** no fim.

---

## Parte 3 — Instalar no celular

1. Passe o `app-debug.apk` para o celular. Formas fáceis:
   - Suba no **Google Drive** (do PC) e baixe pelo Drive no celular; ou
   - Mande por **e-mail para você mesmo** e abra o anexo no celular; ou
   - Conecte o celular no PC por **USB** e copie o arquivo.
2. No celular, **toque no `app-debug.apk`** para instalar.
3. O Android vai avisar que é um app "de fonte desconhecida". Toque em **Configurações** → ative **Permitir desta fonte** (para o app que está abrindo o arquivo, ex.: Arquivos ou Drive) → volte e toque em **Instalar**.
   > Isso é normal para apps que não vêm da Play Store. É seguro — é o app que acabamos de criar.
4. Abra o app **Card de Tarefas** e **entre** com o **mesmo e-mail e senha** que você usa no PC e no site.

---

## Parte 4 — Colocar o widget na tela inicial

1. No app, no campo **"Grupo mostrado no widget"**, deixe **Mercado** (ou o grupo que quiser) e toque em **Salvar**. Adicione alguns itens para testar.
2. Vá para a **tela inicial** do celular.
3. **Segure o dedo** em um espaço vazio da tela → toque em **Widgets**.
4. Procure **Card de Tarefas** → segure o widget **"Lista (marcar itens)"** e arraste para a tela.
5. Pronto: a lista aparece na área de trabalho. **Toque num item para marcá-lo como pego** — ele some da lista e é gravado na nuvem na hora.
   - O **+** abre o app para adicionar itens.
   - O **↻** atualiza a lista na hora.

---

## Como tudo conversa

O app do celular, o widget, o site e o app do PC usam **os mesmos dados** (sua conta no Firebase). Um item criado no PC aparece no celular e vice-versa.

**Detalhe honesto sobre o widget:** por limitação do próprio Android, o widget se atualiza sozinho a cada ~30 minutos e sempre que você marca um item. Se você acabou de mudar algo no PC e quer ver na hora no widget, toque no **↻**.

---

## Se algo der errado

- **A aba Actions está vazia / não compilou:** provavelmente a pasta `.github` não subiu. No repositório, clique em **Add file → Create new file**, e no nome do arquivo digite exatamente:
  `.github/workflows/build.yml`
  Cole o conteúdo do arquivo `build.yml` (está na pasta `.github/workflows` do projeto) e clique em **Commit**. A compilação começa sozinha.
- **A compilação ficou vermelha (❌):** abra a execução, me mande um print da parte vermelha que eu ajusto.
- **O celular não deixa instalar:** confirme que ativou **"Permitir desta fonte"** para o app de onde você abriu o `.apk`.
- **No app aparece "e-mail ou senha incorretos":** use exatamente o mesmo login do site/PC.

Qualquer passo que travar, me chama que a gente destrava junto.

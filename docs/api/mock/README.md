# Mock HTTP do contrato

Este mock permite que o `mykytadu-app` execute jornadas de sucesso e falha sem
subir o backend. A fonte de verdade é sempre o
[`openapi.yaml`](../openapi.yaml); não mantenha respostas JSON copiadas neste
diretório.

O runner usa o [Stoplight Prism CLI](https://github.com/stoplightio/prism)
fixado na versão `5.16.0`. Os exemplos declarados em
`components/examples` do OpenAPI são as fixtures retornadas pelo mock, e o
Prism gera valores para operações que não tenham exemplo explícito.

## Como executar

Pré-requisito: Node.js `>=24.18.0` com `npx` disponível. Na raiz do
repositório:

```powershell
.\docs\api\mock\run-prism.ps1
```

O servidor ficará disponível em `http://127.0.0.1:8082`. Para escolher outra
porta:

```powershell
.\docs\api\mock\run-prism.ps1 -Port 8083
```

Em ambientes Unix-like, use:

```bash
./docs/api/mock/run-prism.sh
```

## Base URL e `/api/v1`

O backend e o contrato usam `http://localhost:8081/api/v1`. O Prism preserva
os caminhos definidos no documento, mas não aplica o prefixo de caminho
presente em `servers.url`. Portanto, no mock a base URL temporária é
`http://127.0.0.1:8082` e as rotas são, por exemplo,
`/auth/login` e `/translations`.

O cliente deve manter os caminhos relativos às operações do contrato. Para
usar o mock, configure somente a base URL do ambiente para
`http://127.0.0.1:8082`; para o backend local real, use
`http://localhost:8081/api/v1`. Esta diferença é específica do adaptador do
Prism e deve ser removida quando o mock receber um proxy com reescrita de
`/api/v1`, se isso se tornar necessário.

## Jornadas rápidas

Os valores de token abaixo são fictícios. O mock não autentica nem persiste
sessões; ele serve para testar o contrato HTTP e a integração do cliente.

Login com resposta de sucesso:

```powershell
curl.exe -i -X POST http://127.0.0.1:8082/auth/login `
  -H "Content-Type: application/json" `
  --data-binary '{"email":"pessoa@example.com","password":"senha-fornecida-pelo-usuario","clientId":"mykytadu-android"}'
```

Reemissão uniforme da verificação de e-mail:

```powershell
curl.exe -i -X POST http://127.0.0.1:8082/auth/verify-email/resend `
  -H "Content-Type: application/json" `
  --data-binary '{"email":"pessoa@example.com"}'
```

Consulta autenticada de perfil:

```powershell
curl.exe -i http://127.0.0.1:8082/me `
  -H "Authorization: Bearer <access-token>"
```

Tradução com sucesso:

```powershell
curl.exe -i -X POST http://127.0.0.1:8082/translations `
  -H "Authorization: Bearer <access-token>" `
  -H "Content-Type: application/json" `
  --data-binary '{"text":"Original anime description","sourceLanguage":"en","targetLanguage":"pt-BR","contentType":"ANIME_DESCRIPTION"}'
```

Falhas modeladas pelo contrato podem ser selecionadas com o header `Prefer`:

```powershell
curl.exe -i -X POST http://127.0.0.1:8082/translations `
  -H "Authorization: Bearer <access-token>" `
  -H "Content-Type: application/json" `
  -H "Prefer: code=429" `
  --data-binary '{"text":"Original anime description","sourceLanguage":"en","targetLanguage":"pt-BR","contentType":"ANIME_DESCRIPTION"}'

curl.exe -i -X POST http://127.0.0.1:8082/translations `
  -H "Authorization: Bearer <access-token>" `
  -H "Content-Type: application/json" `
  -H "Prefer: code=502" `
  --data-binary '{"text":"Original anime description","sourceLanguage":"en","targetLanguage":"pt-BR","contentType":"ANIME_DESCRIPTION"}'

curl.exe -i -X POST http://127.0.0.1:8082/translations `
  -H "Authorization: Bearer <access-token>" `
  -H "Content-Type: application/json" `
  -H "Prefer: code=504" `
  --data-binary '{"text":"Original anime description","sourceLanguage":"en","targetLanguage":"pt-BR","contentType":"ANIME_DESCRIPTION"}'
```

Para verificar o fluxo de autenticação inválida, use `Prefer: code=401` no
login. Para simular falha da primeira entrega após cadastro, use
`Prefer: code=503` em `/auth/register`. Para escolher um exemplo nomeado, o Prism aceita também
`Prefer: example=<nome-do-exemplo>`, conforme a documentação do CLI.

O runner usa `--errors`, permitindo que entradas inválidas produzam erro de
validação do próprio mock. A forma canônica dos erros da API continua sendo a
definida no OpenAPI e no [catálogo de erros](../catalogo-de-erros.md).

## Limites

Este artefato é destinado ao desenvolvimento local e aos testes do cliente.
Não implementa autenticação, CSRF, rate limiting, persistência ou o provedor
de tradução e não deve ser usado como staging ou produção. A validação
automatizada do contrato no CI pertence à B0.2-T5.

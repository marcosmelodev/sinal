-- POLÍCIA MILITAR
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('PM_ASSALTO', 'POLICIA_MILITAR', 'Assalto / Roubo em andamento', 1, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('PM_AGRESSAO', 'POLICIA_MILITAR', 'Agressão física / Briga', 2, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('PM_VIOLENCIA_DOMESTICA', 'POLICIA_MILITAR', 'Violência doméstica / Mulher em perigo', 3, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('PM_OUTRO', 'POLICIA_MILITAR', 'Outro / Não sei informar', 99, true);

-- SAMU
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('SAMU_DESACORDADO', 'SAMU', 'Pessoa desacordada / Não respira', 1, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('SAMU_INFARTO', 'SAMU', 'Dor forte no peito / Suspeita de infarto', 2, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('SAMU_OUTRO', 'SAMU', 'Outro / Não sei informar', 99, true);

-- CORPO DE BOMBEIROS
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('CBM_INCENDIO', 'CORPO_DE_BOMBEIROS', 'Incêndio em residência / Prédio', 1, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('CBM_ACIDENTE_PRESO', 'CORPO_DE_BOMBEIROS', 'Acidente com pessoa presa nas ferragens', 2, true);
INSERT INTO tb_ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('CBM_OUTRO', 'CORPO_DE_BOMBEIROS', 'Outro / Não sei informar', 99, true);
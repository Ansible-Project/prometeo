package org.gatblau.prometeo;

import net.minidev.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Processor implements Runnable {
    private LogManager _log;
    private Command _cmd;
    private String _processId;
    private List<Object> _payload;
    private String _workDir;

    public Processor(String processId, List<Object> payload, Command cmd, LogManager log, String workDir) {
        _cmd = cmd;
        _log = log;
        _payload = payload;
        _processId = processId;
        _workDir = workDir;
    }

    @Override
    public void run() {
        if (devMode()){
            runDevMode();
        }
        else {
            runProdMode();
        }
    }

    private void runProdMode(){
        try {
            Data data = new Data(_processId, _payload);
            _log.start(data);
            _log.payload(data);
            if (!run(data, getGitCloneCmd(data), EventType.DOWNLOAD_SCRIPTS)) return;
            if (data.hasTag()){
                if (!run(data, getGitCheckoutTagCmd(data), EventType.CHECKOUT_TAG)) return;
            }
            if (!run(data, getAnsibleSetupCmd(data), EventType.SETUP_ANSIBLE)) return;
            if (!run(data, getAnsibleRunCmd(data), EventType.RUN_ANSIBLE)) return;
            if (!run(data, getCleanupCmd(data), EventType.REMOVE_WORKDIR)) return;
            callBack(data);
            _log.shutdown(data);
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void runDevMode(){
        Data data = new Data(_processId, _payload);
        _log.startDevMode(data);
        _log.payload(data);
        if (!run(data, getAnsibleDevModeRunCmd(data), EventType.RUN_ANSIBLE)) return;
        callBack(data);
        _log.shutdown(data);
    }

    private boolean run(Data data, String[] cmd, EventType eventType) {
        Command.Result r = _cmd.execute(cmd, _workDir);
        if (r.exitVal == 0) {
            _log.process(data, r.output, ArrayToString(cmd), eventType, true, null);
            return true;
        } else {
            _log.process(data, null, ArrayToString(cmd), eventType, true, r.output);
            _log.shutdown(data);
            return false;
        }
    }

    private String ArrayToString(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            sb.append(value);
            sb.append(" ");
        }
        String value = sb.toString().trim();
        return (value.isEmpty() ?  null : value);
    }

    private String[] getAnsibleRunCmd(Data data) {
        if (!data.checkMode()) {
            return new String[] {
                "ansible-playbook",
                String.format("./%2$s_%1$s/site.yml", data.getProcessId(), data.getRepoName()),
                "-i",
                String.format("./%2$s_%1$s/inventory", data.getProcessId(), data.getRepoName()),
                String.format("-%s", data.getVerbosity()),
                "--extra-vars",
                data.getVars()
            };
        }
        else {
            return new String[] {
                "ansible-playbook",
                String.format("./%2$s_%1$s/site.yml", data.getProcessId(), data.getRepoName()),
                "-i",
                String.format("./%2$s_%1$s/inventory", data.getProcessId(), data.getRepoName()),
                String.format("-%s", data.getVerbosity()),
                "--check",
                "--extra-vars",
                data.getVars()
            };
        }
    }

    private String[] getAnsibleDevModeRunCmd(Data data) {
        if (!data.checkMode()) {
            return new String[] {
                    "ansible-playbook",
                    String.format("./%1$s/site.yml", data.getRepoName()),
                    "-i",
                    String.format("./%1$s/inventory", data.getRepoName()),
                    String.format("-%s", data.getVerbosity()),
                    "--extra-vars",
                    data.getVars()
            };
        }
        else {
            return new String[] {
                    "ansible-playbook",
                    String.format("./%1$s/site.yml", data.getRepoName()),
                    "-i",
                    String.format("./%1$s/inventory", data.getRepoName()),
                    String.format("-%s", data.getVerbosity()),
                    "--check",
                    "--extra-vars",
                    data.getVars()
            };
        }
    }

    private String[] getAnsibleSetupCmd(Data data) {
        return new String[]{
            "ansible-galaxy",
            "install",
            "-r",
            String.format("./%2$s_%1$s/requirements.yml", data.getProcessId(), data.getRepoName()),
            String.format("--roles-path=./%2$s_%1$s/roles", data.getProcessId(), data.getRepoName())
        };
    }

    private String[] getGitCloneCmd(Data data) {
        return new String[]{
            "git",
            "clone",
            data.getRepoUri(),
            String.format("./%2$s_%1$s", data.getProcessId(), data.getRepoName())
        };
    }

    private String[] getGitCheckoutTagCmd(Data data) {
        return new String[] {
            "cd",
            String.format("./%2$s_%1$s", data.getProcessId(), data.getRepoName()),
            "&&",
            "git",
            "checkout",
            data.getTag()
        };
    }

    private String[] getCleanupCmd(Data data) {
         return new String[]{
             "rm",
             "-rf",
             String.format("./%2$s_%1$s", data.getProcessId(), data.getRepoName())
         };
    }

    private void callBack(Data data) {
        if (data.getCallbackUri() != null && data.getCallbackUri().trim().length() > 0) {
            try {
                RestTemplate client = new RestTemplate();
                Map<String, Object> payload = new HashMap<>();
                payload.put("result", "OK");
                payload.put("processId", data.getProcessId());
                payload.put("repoUri", data.getRepoUri());
                payload.put("tag", data.getTag());
                ResponseEntity<String> response = client.postForEntity(data.getCallbackUri(), getEntity(payload), String.class);
                if (response.getStatusCodeValue() == 200) {
                    _log.callback(data);
                }
                else {
                    _log.callback(data, false, response.getBody());
                }
            }
            catch (Exception ex) {
                _log.callback(data, false, ex.getMessage());
            }
        }
    }

    private HttpEntity<String> getEntity(Map<String, Object> map) {
        JSONObject jsonObject = new JSONObject(map);
        String payload = jsonObject.toString().replace("\\", "");
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("Content-Type", "application/x-yaml");
        return new HttpEntity<String>(payload, requestHeaders);
    }

    private boolean devMode() {
        return !_workDir.equals("/prometeo");
    }
}
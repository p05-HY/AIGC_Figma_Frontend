package com.example.blueheartv.control;

import android.os.Bundle;

interface IRemoteShellService {
    Bundle execute(String command);
    void destroy();
}

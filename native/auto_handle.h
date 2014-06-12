#pragma once

// automatically close handle at the end of the scope
class auto_handle {
private:
    HANDLE v;

public:
    auto_handle() {
        v = INVALID_HANDLE_VALUE;
    }

    auto_handle(HANDLE h) {
        v = h;
    }

    ~auto_handle() {
        if (v!=INVALID_HANDLE_VALUE)
            ::CloseHandle(v);
    }

	operator bool () { return v!=NULL && v!=INVALID_HANDLE_VALUE; }

    operator HANDLE& () {
        return v;
    }

	HANDLE* operator & () {
		return &v;
	}
};

// automatically LocalFree at the end of the scope
template <typename T>
class auto_localmem {
private:
    void*   v;

public:
    auto_localmem() {
		v = NULL;
    }
    auto_localmem(size_t sz) {
		v = NULL;
		allocate(sz);
    }

    ~auto_localmem() {
		free();
	}

	void free() {
        if (v!=NULL)
            ::LocalFree(v);
		v = NULL;
    }

    operator T() {
        return (T)v;
    }

	T operator -> () {
		return (T)v;
	}

	void allocate(size_t sz) {
		free();
		v = ::LocalAlloc(LMEM_FIXED|LMEM_ZEROINIT, sz);
	}
};
